package com.despescar.flightservice.service;

import com.despescar.flightservice.dto.baggage.response.FareResponse;
import com.despescar.flightservice.dto.flights.request.FlightRequest;
import com.despescar.flightservice.dto.flights.response.*;
import com.despescar.flightservice.entity.Airline;
import com.despescar.flightservice.entity.Airport;
import com.despescar.flightservice.entity.Fare;
import com.despescar.flightservice.entity.Flight;
import com.despescar.flightservice.exception.AirlineNotFoundException;
import com.despescar.flightservice.exception.AirportNotFoundException;
import com.despescar.flightservice.exception.FlightNotFoundException;
import com.despescar.flightservice.exception.FlightNumberAlreadyExistsException;
import com.despescar.flightservice.mapper.FlightMapper;
import com.despescar.flightservice.repository.AirlineRepository;
import com.despescar.flightservice.repository.AirportRepository;
import com.despescar.flightservice.repository.FareRepository;
import com.despescar.flightservice.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlightService {

    private final FlightRepository flightRepository;
    private final AirlineRepository airlineRepository;
    private final AirportRepository airportRepository;
    private final FareRepository fareRepository;

    /**
     * Crea un nuevo vuelo.
     */
    @Transactional
    public FlightResponse createFlight(FlightRequest request) {

        if (flightRepository.findByFlightNumber(request.getFlightNumber()).isPresent()) {
            throw new FlightNumberAlreadyExistsException();
        }

        Airline airline = airlineRepository.findById(request.getAirlineId())
                .orElseThrow(AirlineNotFoundException::new);

        Airport originAirport = airportRepository.findById(request.getOriginAirportId())
                .orElseThrow(AirportNotFoundException::new);

        Airport destinationAirport = airportRepository.findById(request.getDestinationAirportId())
                .orElseThrow(AirportNotFoundException::new);

        List<Fare> fares = new ArrayList<>();
        if (request.getFaresId() != null && !request.getFaresId().isEmpty()) {
            fares = fareRepository.findAllById(request.getFaresId());
            if (fares.size() != request.getFaresId().size()) {
                throw new RuntimeException("Una o más tarifas proporcionadas no existen.");
            }
        }

        Flight flight = FlightMapper.toEntity(request);
        flight.setAirline(airline);
        flight.setOriginAirport(originAirport);
        flight.setDestinationAirport(destinationAirport);
        flight.setFares(fares);

        flightRepository.save(flight);

        return FlightMapper.toResponse(flight);
    }

    /**
     * Obtiene todos los vuelos.
     */
    public List<FlightResponse> getAllFlights() {
        return flightRepository.findAll()
                .stream()
                .map(FlightMapper::toResponse)
                .collect(Collectors.toList());
    }

    public FlightSearchResponse searchFlights(String origin, String destination, LocalDate departureDate, LocalDate returnDate, int passengers) {

        // 1. Buscar vuelos de IDA (Outbound)
        LocalDateTime startOfDayDeparture = departureDate.atStartOfDay();
        LocalDateTime endOfDayDeparture = departureDate.atTime(23, 59, 59);

        List<Flight> flightsDeparture = flightRepository.findFlightsForSearch(origin, destination, startOfDayDeparture, endOfDayDeparture);

        List<DetailedFlightResponseDto> departureFlights = flightsDeparture.stream()
                .map(flight -> mapToDetailedFlightDto(flight, origin, destination))
                .collect(Collectors.toList());

        // 2. Buscar vuelos de VUELTA (Return) - Condicional
        List<DetailedFlightResponseDto> returnFlights = new ArrayList<>();

        if (returnDate != null) {
            LocalDateTime startOfDayReturn = returnDate.atStartOfDay();
            LocalDateTime endOfDayReturn = returnDate.atTime(23, 59, 59);

            List<Flight> flightsReturn = flightRepository.findFlightsForSearch(destination, origin, startOfDayReturn, endOfDayReturn);

            returnFlights = flightsReturn.stream()
                    .map(flight -> mapToDetailedFlightDto(flight, origin, destination))
                    .collect(Collectors.toList());
        }

        // 3. Construir Metadatos
        MetadataDto metadata = MetadataDto.builder()
                .totalResults(departureFlights.size() + returnFlights.size())
                .origin(origin)
                .destination(destination)
                .departureDate(departureDate)
                .returnDate(returnDate)
                .passengers(passengers)
                .build();

        // 4. Retornar JSON consolidado
        return FlightSearchResponse.builder()
                .metadata(metadata)
                .departureFlights(departureFlights)
                .returnFlights(returnFlights)
                .build();
    }

    private DetailedFlightResponseDto mapToDetailedFlightDto(Flight flight, String origin, String destination) {
        BigDecimal basePrice = flight.getPrice() != null ? flight.getPrice() : BigDecimal.ZERO;

        List<FareResponse> fareDtos = flight.getFares().stream().map(fare -> FareResponse.builder()
                .id(fare.getId())
                .name(fare.getName())
                .type(fare.getType())
                .includedServices(IncludedServicesDto.builder()
                        .personalItem(fare.isPersonalItem())
                        .carryOn(fare.isCarryOn())
                        .checkedBaggage(fare.isCheckedBaggage())
                        .wifi(fare.isWifi())
                        .seatSelection(fare.getSeatSelection())
                        .build())
                .price(PriceDto.builder()
                        .currency(fare.getCurrency())
                        .baseFare(fare.getBaseFare())
                        .taxesAndFees(fare.getTaxesAndFees())
                        .transparentFinalPrice(fare.getTransparentFinalPrice())
                        .build())
                .build()).toList();

        FareResponse baseFare = fareDtos.stream()
                .min(Comparator.comparing(FlightService::precioDeTarifa))
                .orElse(null);
        // Precio final por pasajero con la tarifa más barata: el mismo que cobra el carrito.
        BigDecimal finalPrice = basePrice.add(baseFare != null ? precioDeTarifa(baseFare) : BigDecimal.ZERO);

        return DetailedFlightResponseDto.builder()
                .id(flight.getId())
                .flightNumber(flight.getFlightNumber())
                .airline(AirlineSearchDto.builder()
                        .name(flight.getAirline() != null ? flight.getAirline().getName() : "Unknown")
                        .logoUrl(flight.getAirline() != null ? flight.getAirline().getLogoUrl() : "")
                        .build())
                .aircraft("Boeing 737-800")
                .itinerary(ItineraryDto.builder()
                        .departure(FlightLegDto.builder()
                                .iata(flight.getOriginAirport() != null ? flight.getOriginAirport().getCode() : origin)
                                .dateTime(flight.getDepartureTime().toString())
                                .build())
                        .arrival(FlightLegDto.builder()
                                .iata(flight.getDestinationAirport() != null ? flight.getDestinationAirport().getCode() : destination)
                                .dateTime(flight.getArrivalTime().toString())
                                .build())
                        .durationMinutes(120)
                        .flightType("DIRECTO")
                        .build())
                .scales(List.of())
                .includedServices(baseFare != null ? baseFare.getIncludedServices() : null)
                .price(PriceDto.builder()
                        .currency("ARS")
                        .baseFare(basePrice)
                        .taxesAndFees(BigDecimal.ZERO)
                        .transparentFinalPrice(finalPrice)
                        .build())
                .fares(fareDtos)
                .build();
    }

    private static BigDecimal precioDeTarifa(FareResponse fare) {
        return fare.getPrice() != null && fare.getPrice().getTransparentFinalPrice() != null
                ? fare.getPrice().getTransparentFinalPrice()
                : BigDecimal.ZERO;
    }

    /**
     * Busca un vuelo por ID.
     */
    public FlightResponse getFlightById(UUID id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(FlightNotFoundException::new);

        return FlightMapper.toResponse(flight);
    }

    /**
     * Busca un vuelo por número.
     */
    public FlightResponse getFlightByNumber(String flightNumber) {
        Flight flight = flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(FlightNotFoundException::new);

        return FlightMapper.toResponse(flight);
    }

    /**
     * Actualiza un vuelo existente.
     */
    @Transactional
    public FlightResponse updateFlight(UUID id, FlightRequest request) {

        Flight flight = flightRepository.findById(id)
                .orElseThrow(FlightNotFoundException::new);

        Airline airline = airlineRepository.findById(request.getAirlineId())
                .orElseThrow(AirlineNotFoundException::new);

        Airport originAirport = airportRepository.findById(request.getOriginAirportId())
                .orElseThrow(AirportNotFoundException::new);

        Airport destinationAirport = airportRepository.findById(request.getDestinationAirportId())
                .orElseThrow(AirportNotFoundException::new);

        FlightMapper.updateEntity(flight, request);

        flight.setAirline(airline);
        flight.setOriginAirport(originAirport);
        flight.setDestinationAirport(destinationAirport);

        if (request.getFaresId() != null && !request.getFaresId().isEmpty()) {
            List<Fare> newFares = fareRepository.findAllById(request.getFaresId());
            if (newFares.size() != request.getFaresId().size()) {
                throw new RuntimeException("Una o más tarifas proporcionadas no existen.");
            }
            flight.setFares(newFares); // 👈 Asignamos la lista directamente
        } else {
            flight.setFares(new ArrayList<>());
        }

        flightRepository.save(flight);

        return FlightMapper.toResponse(flight);
    }

    /**
     * Elimina un vuelo.
     */
    public void deleteFlight(UUID id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(FlightNotFoundException::new);

        flightRepository.delete(flight);
    }

    /**
     * Ajusta los asientos disponibles de un vuelo de forma atómica.
     * delta negativo para reservar, positivo para liberar.
     */
    @Transactional
    public void adjustSeats(String flightNumber, int delta) {
        Flight flight = flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(FlightNotFoundException::new);

        int nuevosAsientos = flight.getAvailableSeats() + delta;
        if (nuevosAsientos < 0) {
            throw new IllegalStateException(
                    "No hay suficientes asientos disponibles en het vuelo " + flightNumber +
                            ". Disponibles: " + flight.getAvailableSeats() + ", solicitados: " + (-delta)
            );
        }

        flight.setAvailableSeats(nuevosAsientos);
        flightRepository.save(flight);
    }

    /**
     * Obtiene todos los vuelos de una aerolínea.
     */
    public List<FlightResponse> getFlightsByAirline(UUID airlineId) {
        return flightRepository.findByAirlineId(airlineId)
                .stream()
                .map(FlightMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los vuelos cuyo origen es un aeropuerto.
     */
    public List<FlightResponse> getFlightsByOrigin(UUID airportId) {
        return flightRepository.findByOriginAirportId(airportId)
                .stream()
                .map(FlightMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los vuelos cuyo destino es un aeropuerto.
     */
    public List<FlightResponse> getFlightsByDestination(UUID airportId) {
        return flightRepository.findByDestinationAirportId(airportId)
                .stream()
                .map(FlightMapper::toResponse)
                .collect(Collectors.toList());
    }
}