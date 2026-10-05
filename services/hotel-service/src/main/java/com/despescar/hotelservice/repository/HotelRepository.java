package com.despescar.hotelservice.repository;

import com.despescar.hotelservice.entity.Hotel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelRepository extends JpaRepository<Hotel, UUID> {

    List<Hotel> findByActivoTrue();

    Optional<Hotel> findByIdAndActivoTrue(UUID id);
}
