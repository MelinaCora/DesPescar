package com.despescar.packageservice.service;

import com.despescar.packageservice.dto.request.TravelPackageRequest;
import com.despescar.packageservice.dto.response.TravelPackageResponse;
import com.despescar.packageservice.entity.TourPackage;
import com.despescar.packageservice.exception.PackageInactiveException;
import com.despescar.packageservice.repository.TourPackageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TourPackageServiceTest {

    @Autowired
    private TourPackageService tourPackageService;

    @Autowired
    private TourPackageRepository tourPackageRepository;

    @Test
    void shouldDeactivateAndReactivatePackage() {
        TravelPackageRequest request = baseRequest("Pack Iguazu");

        Long id = tourPackageService.create(request).getId();

        tourPackageService.deactivate(id);
        TourPackage inactivePackage = tourPackageRepository.findById(id).orElseThrow();
        assertFalse(inactivePackage.isActive());

        tourPackageService.activate(id);
        TourPackage activePackage = tourPackageRepository.findById(id).orElseThrow();
        assertTrue(activePackage.isActive());
    }

    @Test
    void shouldRejectUpdatesForInactivePackages() {
        TravelPackageRequest request = baseRequest("Pack Bariloche");

        Long id = tourPackageService.create(request).getId();
        tourPackageService.deactivate(id);

        TravelPackageRequest updateRequest = baseRequest("Pack Bariloche Plus");

        assertThrows(PackageInactiveException.class, () -> tourPackageService.update(id, updateRequest));
    }

    @Test
    void shouldNormalizeBlankFlightNumberToNull() {
        TravelPackageRequest request = baseRequest("Pack Mendoza");
        request.setFlightNumber("   ");

        Long id = tourPackageService.create(request).getId();
        TourPackage savedPackage = tourPackageRepository.findById(id).orElseThrow();

        assertNull(savedPackage.getFlightNumber());
    }

    @Test
    void shouldFilterPackagesByDestinationActiveAndPrice() {
        tourPackageService.create(baseRequest("Pack Costa"));

        TravelPackageRequest inactiveRequest = baseRequest("Pack Nieve");
        inactiveRequest.setDestination("Patagonia");
        inactiveRequest.setBasePrice(BigDecimal.valueOf(400000));
        Long inactiveId = tourPackageService.create(inactiveRequest).getId();
        tourPackageService.deactivate(inactiveId);

        TravelPackageRequest premiumRequest = baseRequest("Pack Premium");
        premiumRequest.setDestination("Patagonia");
        premiumRequest.setBasePrice(BigDecimal.valueOf(800000));
        tourPackageService.create(premiumRequest);

        List<TravelPackageResponse> filtered = tourPackageService.search(
                "Patagonia",
                true,
                BigDecimal.valueOf(500000),
                BigDecimal.valueOf(900000)
        );

        assertTrue(filtered.stream().allMatch(pkg -> pkg.isActive() && pkg.getDestination().equals("Patagonia")));
        assertTrue(filtered.stream().allMatch(pkg -> pkg.getBasePrice().compareTo(BigDecimal.valueOf(500000)) >= 0));
        assertTrue(filtered.stream().allMatch(pkg -> pkg.getBasePrice().compareTo(BigDecimal.valueOf(900000)) <= 0));
    }

    @Test
    void shouldRejectInvalidPriceRange() {
        assertThrows(IllegalArgumentException.class, () ->
                tourPackageService.search(null, null, BigDecimal.valueOf(900000), BigDecimal.valueOf(100000))
        );
    }

    private TravelPackageRequest baseRequest(String name) {
        return TravelPackageRequest.builder()
                .name(name)
                .description("Un paquete turistico completo")
                .destination("Argentina")
                .flightNumber(null)
                .durationNights(4)
                .basePrice(BigDecimal.valueOf(250000))
                .build();
    }
}
