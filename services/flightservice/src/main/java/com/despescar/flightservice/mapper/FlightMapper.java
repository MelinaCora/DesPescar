package com.despescar.flightservice.mapper;

import com.despescar.flightservice.dto.flights.request.FlightRequest;
import com.despescar.flightservice.dto.flights.response.FlightResponse;
import com.despescar.flightservice.entity.Airline;
import com.despescar.flightservice.entity.Airport;
import com.despescar.flightservice.entity.Flight;

public class FlightMapper {

    private FlightMapper() {
    }

    /**
     * Convierte un FlightRequest a Flight.
     * Las entidades Airline y Airport deben ser buscadas previamente en el Service.
     */
    public static Flight toEntity(
            FlightRequest request,
            Airline airline,
            Airport originAirport,
            Airport destinationAirport) {

        return Flight.builder()
                .flightNumber(request.getFlightNumber())
                .airline(airline)
                .originAirport(originAirport)
                .destinationAirport(destinationAirport)
                .departureTime(request.getDepartureTime())
                .arrivalTime(request.getArrivalTime())
                .price(request.getPrice())
                .availableSeats(request.getAvaibleSeates())
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
                .build();
    }
}