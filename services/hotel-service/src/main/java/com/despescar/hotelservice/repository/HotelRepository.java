package com.despescar.hotelservice.repository;

import com.despescar.hotelservice.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, UUID> {

    List<Hotel> findByCiudadIgnoreCase(String ciudad);
}