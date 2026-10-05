package com.despescar.hotelservice.domain;

import com.despescar.hotelservice.entity.TramoCancelacion;
import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PoliticaCancelacionValidator {

    private PoliticaCancelacionValidator() {
    }

    public static void validar(List<TramoCancelacion> tramos) {
        if (tramos == null || tramos.isEmpty()) {
            throw new SolicitudInvalidaException("La política de cancelación necesita al menos un tramo.");
        }
        Set<Integer> horas = new HashSet<>();
        for (TramoCancelacion tramo : tramos) {
            if (tramo.getHorasAntes() < 0) {
                throw new SolicitudInvalidaException("Las horas de un tramo no pueden ser negativas.");
            }
            if (tramo.getPorcentajeReembolso() < 0 || tramo.getPorcentajeReembolso() > 100) {
                throw new SolicitudInvalidaException("El porcentaje de reembolso va de 0 a 100.");
            }
            if (!horas.add(tramo.getHorasAntes())) {
                throw new SolicitudInvalidaException("Hay dos tramos con las mismas horas.");
            }
        }
        List<TramoCancelacion> ordenados = tramos.stream()
                .sorted(Comparator.comparingInt(TramoCancelacion::getHorasAntes).reversed())
                .toList();
        for (int i = 1; i < ordenados.size(); i++) {
            if (ordenados.get(i).getPorcentajeReembolso() > ordenados.get(i - 1).getPorcentajeReembolso()) {
                throw new SolicitudInvalidaException(
                        "El reembolso no puede aumentar a medida que se acerca la fecha.");
            }
        }
    }
}
