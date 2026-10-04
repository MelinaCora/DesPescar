package com.despescar.payment_service.service;

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
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentFractionRepository fractionRepository;
    private final PaymentGroupRepository groupRepository;

    @Value("${mercadopago.access-token}")
    private String mpAccessToken;

    @PostConstruct
    public void init() {
        // Inicializa el SDK con tu credencial de producción/test
        MercadoPagoConfig.setAccessToken(mpAccessToken);
    }


    @Value("${mercadopago.access-token}")
    private String mercadoPagoAccessToken;

    @Transactional
    public void authorizeFractionPayment(PaymentAuthRequestDTO request) throws MPException, MPApiException {

        // 1. Validar que la fracción existe y está pendiente
        PaymentFraction fraction = fractionRepository.findById(request.getFractionId())
                .orElseThrow(() -> new RuntimeException("Fracción de pago no encontrada"));

        if (!fraction.getStatus().equals(PaymentStatus.PENDING)) {
            throw new RuntimeException("Esta fracción ya fue procesada.");
        }

        PaymentGroup group = fraction.getPaymentGroup();

        MercadoPagoConfig.setAccessToken(mercadoPagoAccessToken);

        // 2. Configurar el request para Mercado Pago reteniendo los fondos
        PaymentClient client = new PaymentClient();
        PaymentCreateRequest paymentCreateRequest = PaymentCreateRequest.builder()
                .transactionAmount(fraction.getAmount())
                .token(request.getToken()) // Token seguro desde React
                .description("Reserva de vuelo #" + group.getReservationId() + " - Parte de pasajero")
                .installments(request.getInstallments())
                .paymentMethodId(request.getPaymentMethodId())
                .issuerId(request.getIssuerId())
                .payer(PaymentPayerRequest.builder()
                        .email(request.getPayerEmail())
                        .build())
                .capture(false) // ESTA ES LA CLAVE: Congela, no cobra.
                .externalReference(group.getId().toString())
                .build();

        // 3. Ejecutar la llamada a la API
        Payment mpPayment = client.create(paymentCreateRequest);

        // 4. Evaluar el resultado
        if ("authorized".equals(mpPayment.getStatus())) {
            fraction.setStatus(PaymentStatus.AUTHORIZED);
            fraction.setMpPaymentId(mpPayment.getId().toString());
            fractionRepository.save(fraction);

            log.info("Pago autorizado retenido en tarjeta. MP ID: {}", mpPayment.getId());

            // 5. Verificar si todo el grupo ya está autorizado
            checkAndUpdateGroupStatus(group);
        } else {
            // Manejar tarjetas rechazadas o sin fondos
            fraction.setStatus(PaymentStatus.CANCELLED);
            fractionRepository.save(fraction);
            throw new RuntimeException("El pago fue rechazado por el banco. Estado MP: " + mpPayment.getStatusDetail());
        }
    }

    private void checkAndUpdateGroupStatus(PaymentGroup group) {
        // Verifica si TODAS las fracciones del grupo están autorizadas
        boolean allAuthorized = group.getFractions().stream()
                .allMatch(f -> f.getStatus().equals(PaymentStatus.AUTHORIZED));

        if (allAuthorized) {
            group.setStatus(PaymentStatus.AUTHORIZED_READY);
            groupRepository.save(group);
            log.info("El grupo de pago para la reserva {} está completo. Listo para CAPTURA FINAL.", group.getReservationId());

            // Aquí puedes disparar un Evento en Spring o llamar directamente al método de captura
            // captureFullGroup(group);
        }
    }

    @Transactional
    public PaymentGroup createPaymentGroup(Long reservationId, BigDecimal totalAmount, List<Long> userIds) {
        // 1. Validar que no exista un grupo activo para esta reserva
        groupRepository.findByReservationId(reservationId).ifPresent(g -> {
            throw new RuntimeException("Ya existe un grupo de pago para esta reserva.");
        });

        // 2. Crear el Grupo Maestro
        PaymentGroup group = PaymentGroup.builder()
                .reservationId(reservationId)
                .totalAmount(totalAmount)
                .status(PaymentStatus.PENDING)
                // Damos 15 minutos exactos para que todos paguen
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        group = groupRepository.save(group);

        // 3. Dividir el monto y crear las fracciones
        BigDecimal fractionAmount = totalAmount.divide(new BigDecimal(userIds.size()), 2, java.math.RoundingMode.HALF_UP);

        for (Long userId : userIds) {
            PaymentFraction fraction = PaymentFraction.builder()
                    .paymentGroup(group)
                    .userId(userId)
                    .amount(fractionAmount)
                    .status(PaymentStatus.PENDING)
                    .build();

            group.getFractions().add(fraction);
        }

        // Guarda el grupo completo con sus fracciones en cascada
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
                    // MP Payment IDs son números grandes, los parseamos a Long
                    Long mpId = Long.valueOf(fraction.getMpPaymentId());

                    // Ejecutamos la captura del dinero congelado
                    Payment capturedPayment = client.capture(mpId);

                    if ("approved".equals(capturedPayment.getStatus())) {
                        fraction.setStatus(PaymentStatus.CAPTURED);
                        fractionRepository.save(fraction);
                        log.info("Cobro capturado con éxito para la fracción {}", fraction.getId());
                    } else {
                        log.error("Fallo al capturar fracción {}: {}", fraction.getId(), capturedPayment.getStatusDetail());
                        // Aquí podrías implementar una reversión manual si falla la captura de uno
                    }
                } catch (MPException | MPApiException e) {
                    log.error("Excepción de MP al capturar el pago {}: {}", fraction.getMpPaymentId(), e.getMessage());
                }
            }
        }

        // Validar si TODAS las fracciones pasaron a CAPTURED
        boolean allCaptured = group.getFractions().stream()
                .allMatch(f -> f.getStatus().equals(PaymentStatus.CAPTURED));

        if (allCaptured) {
            group.setStatus(PaymentStatus.CAPTURED);
            groupRepository.save(group);
            log.info("¡ÉXITO! Se ha cobrado el 100% de la reserva {}.", group.getReservationId());

            // ---> AQUÍ ES DONDE LLAMAS A TU MICROSERVICIO DE RESERVAS <---
            // Ej: reservationClient.confirmAndEmitTickets(group.getReservationId());
        } else {
            log.warn("ATENCIÓN: El grupo {} no se pudo capturar por completo.", group.getId());
        }
    }
}