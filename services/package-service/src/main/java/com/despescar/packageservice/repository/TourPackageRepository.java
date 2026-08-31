package com.despescar.packageservice.repository;

import com.despescar.packageservice.entity.TourPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TourPackageRepository extends JpaRepository<TourPackage, Long> {

    List<TourPackage> findAllByOrderByNameAsc();

    Optional<TourPackage> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
