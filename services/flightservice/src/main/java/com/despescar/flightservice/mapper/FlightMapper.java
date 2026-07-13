package com.despescar.flightservice.mapper;

import com.despescar.flightservice.dto.flights.request.FlightRequest;
import com.despescar.flightservice.dto.flights.response.FlightResponse;
import com.despescar.flightservice.entity.Airline;
import com.despescar.flightservice.entity.Airport;
import com.despescar.flightservice.entity.Flight;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor // Permite inyectar los otros mappers de manera automática
public class FlightMapper {

    private final AirlineMapper airlineMapper;
    private final AirportMapper airportMapper;

    /**
     * Convierte un FlightRequest a Flight sin asociar relaciones (se asocian en el Service).
     */
    public Flight toEntity(FlightRequest request) {
        if (request == null) {
            return null;
        }
        return Flight.builder()
                .flightNumber(request.getFlightNumber())
                .departureTime(request.getDepartureTime())
                .arrivalTime(request.getArrivalTime())
                .price(request.getPrice())
                .availableSeats(request.getAvailableSeats()) // Ojo con el tipeo de tu DTO (avaibleSeates)
                .build();
    }

    /**
     * Convierte una entidad Flight a FlightResponse usando los mappers inyectados.
     */
    public FlightResponse toResponse(Flight flight) {
        if (flight == null) {
            return null;
        }
        return FlightResponse.builder()
                .id(flight.getId())
                .flightNumber(flight.getFlightNumber())
                // Usamos las instancias inyectadas en minúscula
                .airline(flight.getAirline() != null ? airlineMapper.toResponse(flight.getAirline()) : null)
                .originAirport(flight.getOriginAirport() != null ? airportMapper.toResponse(flight.getOriginAirport()) : null)
                .destinationAirport(flight.getDestinationAirport() != null ? airportMapper.toResponse(flight.getDestinationAirport()) : null)
                .departureTime(flight.getDepartureTime())
                .arrivalTime(flight.getArrivalTime())
                .price(flight.getPrice())
                .availableSeats(flight.getAvailableSeats())
                .status(flight.getStatus())
                .build();
    }

    /**
     * Actualiza los datos de un vuelo existente con la información de la solicitud.
     */
    public void updateEntity(Flight flight, FlightRequest request) {
        if (flight == null || request == null) {
            return;
        }
        flight.setFlightNumber(request.getFlightNumber());
        flight.setDepartureTime(request.getDepartureTime());
        flight.setArrivalTime(request.getArrivalTime());
        flight.setPrice(request.getPrice());
        flight.setAvailableSeats(request.getAvailableSeats());
    }
}