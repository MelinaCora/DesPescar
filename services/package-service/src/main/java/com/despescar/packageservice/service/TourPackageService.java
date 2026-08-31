package com.despescar.packageservice.service;

import com.despescar.packageservice.dto.request.TravelPackageRequest;
import com.despescar.packageservice.dto.response.TravelPackageResponse;
import com.despescar.packageservice.entity.TourPackage;
import com.despescar.packageservice.exception.DuplicatePackageException;
import com.despescar.packageservice.exception.PackageInactiveException;
import com.despescar.packageservice.exception.PackageNotFoundException;
import com.despescar.packageservice.repository.TourPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TourPackageService {

    private final TourPackageRepository tourPackageRepository;

    @Transactional
    public TravelPackageResponse create(TravelPackageRequest request) {
        ensureUniqueName(request.getName(), null);
        TourPackage tourPackage = mapRequestToEntity(new TourPackage(), request);
        return toResponse(tourPackageRepository.save(tourPackage));
    }

    @Transactional(readOnly = true)
    public List<TravelPackageResponse> findAll() {
        return tourPackageRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TravelPackageResponse> search(
            String destination,
            Boolean active,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        validatePriceRange(minPrice, maxPrice);

        String normalizedDestination = normalizeOptionalText(destination);
        return tourPackageRepository.findAllByOrderByNameAsc().stream()
                .filter(tourPackage -> matchesDestination(tourPackage, normalizedDestination))
                .filter(tourPackage -> matchesActive(tourPackage, active))
                .filter(tourPackage -> matchesMinPrice(tourPackage, minPrice))
                .filter(tourPackage -> matchesMaxPrice(tourPackage, maxPrice))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TravelPackageResponse findById(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public TravelPackageResponse update(Long id, TravelPackageRequest request) {
        TourPackage tourPackage = getEntity(id);
        ensureActive(tourPackage);
        ensureUniqueName(request.getName(), id);
        mapRequestToEntity(tourPackage, request);
        return toResponse(tourPackageRepository.save(tourPackage));
    }

    @Transactional
    public void deactivate(Long id) {
        TourPackage tourPackage = getEntity(id);
        if (!tourPackage.isActive()) {
            return;
        }

        tourPackage.setActive(false);
        tourPackageRepository.save(tourPackage);
    }

    @Transactional
    public TravelPackageResponse activate(Long id) {
        TourPackage tourPackage = getEntity(id);
        if (tourPackage.isActive()) {
            return toResponse(tourPackage);
        }

        tourPackage.setActive(true);
        return toResponse(tourPackageRepository.save(tourPackage));
    }

    private TourPackage getEntity(Long id) {
        return tourPackageRepository.findById(id)
                .orElseThrow(() -> new PackageNotFoundException(id));
    }

    private void ensureUniqueName(String name, Long currentId) {
        tourPackageRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new DuplicatePackageException(name);
            }
        });
    }

    private void ensureActive(TourPackage tourPackage) {
        if (!tourPackage.isActive()) {
            throw new PackageInactiveException(tourPackage.getId());
        }
    }

    private TourPackage mapRequestToEntity(TourPackage tourPackage, TravelPackageRequest request) {
        tourPackage.setName(request.getName().trim());
        tourPackage.setDescription(request.getDescription().trim());
        tourPackage.setDestination(request.getDestination().trim());
        tourPackage.setFlightNumber(normalizeOptionalText(request.getFlightNumber()));
        tourPackage.setHotelId(request.getHotelId());
        tourPackage.setDurationNights(request.getDurationNights());
        tourPackage.setBasePrice(request.getBasePrice());
        return tourPackage;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("El precio minimo no puede ser mayor que el precio maximo.");
        }
    }

    private boolean matchesDestination(TourPackage tourPackage, String destination) {
        if (destination == null) {
            return true;
        }

        return tourPackage.getDestination() != null
                && tourPackage.getDestination().toLowerCase(Locale.ROOT).contains(destination.toLowerCase(Locale.ROOT));
    }

    private boolean matchesActive(TourPackage tourPackage, Boolean active) {
        return active == null || tourPackage.isActive() == active;
    }

    private boolean matchesMinPrice(TourPackage tourPackage, BigDecimal minPrice) {
        return minPrice == null || tourPackage.getBasePrice().compareTo(minPrice) >= 0;
    }

    private boolean matchesMaxPrice(TourPackage tourPackage, BigDecimal maxPrice) {
        return maxPrice == null || tourPackage.getBasePrice().compareTo(maxPrice) <= 0;
    }

    private TravelPackageResponse toResponse(TourPackage tourPackage) {
        return TravelPackageResponse.builder()
                .id(tourPackage.getId())
                .name(tourPackage.getName())
                .description(tourPackage.getDescription())
                .destination(tourPackage.getDestination())
                .flightNumber(tourPackage.getFlightNumber())
                .hotelId(tourPackage.getHotelId())
                .durationNights(tourPackage.getDurationNights())
                .basePrice(tourPackage.getBasePrice())
                .active(tourPackage.isActive())
                .createdAt(tourPackage.getCreatedAt())
                .updatedAt(tourPackage.getUpdatedAt())
                .build();
    }
}
