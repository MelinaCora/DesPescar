package com.despescar.flightservice.mapper;

import com.despescar.flightservice.dto.flights.request.FlightRequest;
import com.despescar.flightservice.dto.flights.response.FlightResponse;
import com.despescar.flightservice.entity.Flight;
import com.despescar.flightservice.dto.baggage.response.BaggagePolicyResponse;


public class FlightMapper {

    private FlightMapper() {
        // Evita instanciar la clase
    }

    /**
     * Convierte un FlightRequest a una entidad Flight.
     * Las relaciones (Airline y Airport) se asignan en el Service.
     */

    /**
     * Convierte un FlightRequest a Flight sin asociar relaciones (se asocian en el Service).
     */
    public static Flight toEntity(FlightRequest request) {

        return Flight.builder()
                .flightNumber(request.getFlightNumber())
                .departureTime(request.getDepartureTime())
                .arrivalTime(request.getArrivalTime())
                .price(request.getPrice())
                .availableSeats(request.getAvailableSeats())
                .status(request.getStatus())
                .build();
    }

    /**
     * Convierte una entidad Flight a FlightResponse.
     */
    public static FlightResponse toResponse(Flight flight) {

        return FlightResponse.builder()
                .id(flight.getId())
                .flightNumber(flight.getFlightNumber())
                .airline(AirlineMapper.toResponse(flight.getAirline()))
                .originAirport(AirportMapper.toResponse(flight.getOriginAirport()))
                .destinationAirport(AirportMapper.toResponse(flight.getDestinationAirport()))
                .departureTime(flight.getDepartureTime())
                .arrivalTime(flight.getArrivalTime())
                .price(flight.getPrice())
                .availableSeats(flight.getAvailableSeats())
                .status(flight.getStatus())
                .baggagePolicy(
                        BaggagePolicyMapper.toResponse(flight.getBaggagePolicy())
                )
                .build();
    }

    /**
     * Actualiza una entidad existente con los datos del request.
     * No modifica las relaciones, eso se hace desde el Service.
     */
    public static void updateEntity(Flight flight, FlightRequest request) {

        flight.setFlightNumber(request.getFlightNumber());
        flight.setDepartureTime(request.getDepartureTime());
        flight.setArrivalTime(request.getArrivalTime());
        flight.setPrice(request.getPrice());
        flight.setAvailableSeats(request.getAvailableSeats());
    }
}