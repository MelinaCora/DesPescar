package com.despescar.payment_service.service;

import com.despescar.payment_service.client.ReservationClient;
import com.despescar.payment_service.client.dto.ReservationResponse;
import com.despescar.payment_service.dto.request.PaymentAuthRequestDTO;
import com.despescar.payment_service.dto.response.FractionResponseDTO;
import com.despescar.payment_service.dto.response.PaymentGroupResponseDTO;
import com.despescar.payment_service.entity.PaymentFraction;
import com.despescar.payment_service.entity.PaymentGroup;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.repository.PaymentFractionRepository;
import com.despescar.payment_service.repository.PaymentGroupRepository;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    // 1. Declaración limpia (Sin duplicados)
    private final PaymentFractionRepository fractionRepository;
    private final PaymentGroupRepository groupRepository;
    private final ReservationClient reservationClient;

    @Value("${mercadopago.access-token}")
    private String mpAccessToken;

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(mpAccessToken);
    }

    @Transactional
    public boolean authorizeFractionPayment(PaymentAuthRequestDTO request) {
        PaymentFraction fraction = fractionRepository.findById(request.getFractionId())
                .orElseThrow(() -> new RuntimeException("Fracción no encontrada"));

        // --- MOCK DE MERCADO PAGO (TEMPORAL) ---
        log.info("SIMULANDO PAGO EN MP CON TOKEN: {}", request.getToken());
        Long fakeMpPaymentId = (long) (Math.random() * 10000000000L);

        fraction.setMpPaymentId(fakeMpPaymentId.toString());
        fraction.setStatus(PaymentStatus.AUTHORIZED);
        fractionRepository.save(fraction);

        log.info("Fracción {} autorizada exitosamente (MOCK).", fraction.getId());

        // 3. Ejecuta lógica de negocio, mandando el ID del usuario y el token de pago simulado
        return checkAndUpdateGroupStatus(fraction.getPaymentGroup(), fraction.getUserId(), fakeMpPaymentId.toString());
    }

    private boolean checkAndUpdateGroupStatus(PaymentGroup group, Long lastPayerId, String mpToken) {
        boolean allAuthorized = group.getFractions().stream()
                .allMatch(f -> f.getStatus().equals(PaymentStatus.AUTHORIZED));

        if (allAuthorized) {
            group.setStatus(PaymentStatus.AUTHORIZED_READY);
            groupRepository.save(group);
            log.info("El grupo {} está completo. Sincronizando con Reservation-Service.", group.getReservationId());

            // 4. Llama a tu cliente para confirmar el pago en el microservicio de reservas
            reservationClient.markReservationPaymentPaid(group.getReservationId(), lastPayerId, mpToken);
        }

        return allAuthorized;
    }

    @Transactional
    public PaymentGroup createPaymentGroup(Long reservationId, List<Long> userIds) {

        // 1. Validar que no exista un grupo activo
        groupRepository.findByReservationId(reservationId).ifPresent(g -> {
            throw new RuntimeException("Ya existe un grupo de pago para esta reserva.");
        });

        // 2. Traer la reserva real SÓLO para validar existencia y obtener el monto inalterable
        ReservationResponse reservation = reservationClient.getReservation(reservationId);

        if (reservation == null || reservation.getMontoTotal() == null) {
            throw new RuntimeException("La reserva no es válida o no tiene monto.");
        }

        if (userIds == null || userIds.isEmpty()) {
            throw new RuntimeException("Debe haber al menos un usuario para procesar el pago.");
        }

        // 3. Crear el Grupo Maestro
        PaymentGroup group = PaymentGroup.builder()
                .reservationId(reservationId)
                .totalAmount(reservation.getMontoTotal())
                .status(PaymentStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        // 4. Dividir el monto real por la cantidad de amigos
        BigDecimal fractionAmount = reservation.getMontoTotal().divide(
                new BigDecimal(userIds.size()),
                2,
                java.math.RoundingMode.HALF_UP
        );

        // 5. Crear fracciones basándose en la lista de usuarios del frontend
        List<PaymentFraction> fractions = userIds.stream().map(userId ->
                PaymentFraction.builder()
                        .paymentGroup(group)
                        .userId(userId)
                        .amount(fractionAmount)
                        .status(PaymentStatus.PENDING)
                        .build()
        ).collect(Collectors.toList());

        group.setFractions(fractions);

        return groupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public PaymentGroupResponseDTO getGroupResponse(Long reservationId) {
        PaymentGroup group = groupRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new RuntimeException("Grupo no encontrado para la reserva: " + reservationId));

        List<FractionResponseDTO> fractionsDto = group.getFractions().stream()
                .map(f -> FractionResponseDTO.builder()
                        .fractionId(f.getId())
                        .userId(f.getUserId())
                        .amount(f.getAmount())
                        .status(f.getStatus())
                        .build())
                .toList();

        return PaymentGroupResponseDTO.builder()
                .groupId(group.getId())
                .reservationId(group.getReservationId())
                .totalAmount(group.getTotalAmount())
                .status(group.getStatus())
                .expiresAt(group.getExpiresAt())
                .fractions(fractionsDto)
                .build();
    }

    @Transactional
    public void captureFullGroup(PaymentGroup group) {
        PaymentClient client = new PaymentClient();

        for (PaymentFraction fraction : group.getFractions()) {
            if (fraction.getStatus().equals(PaymentStatus.AUTHORIZED)) {
                try {
                    Long mpId = Long.valueOf(fraction.getMpPaymentId());
                    Payment capturedPayment = client.capture(mpId);

                    if ("approved".equals(capturedPayment.getStatus())) {
                        fraction.setStatus(PaymentStatus.CAPTURED);
                        fractionRepository.save(fraction);
                        log.info("Cobro capturado con éxito para la fracción {}", fraction.getId());
                    } else {
                        log.error("Fallo al capturar fracción {}: {}", fraction.getId(), capturedPayment.getStatusDetail());
                    }
                } catch (MPException | MPApiException e) {
                    log.error("Excepción de MP al capturar el pago {}: {}", fraction.getMpPaymentId(), e.getMessage());
                }
            }
        }

        boolean allCaptured = group.getFractions().stream()
                .allMatch(f -> f.getStatus().equals(PaymentStatus.CAPTURED));

        if (allCaptured) {
            group.setStatus(PaymentStatus.CAPTURED);
            groupRepository.save(group);
            log.info("¡ÉXITO! Se ha cobrado el 100% de la reserva {}.", group.getReservationId());
        } else {
            log.warn("ATENCIÓN: El grupo {} no se pudo capturar por completo.", group.getId());
        }
    }
}