package com.despescar.hotelservice.controller;

import com.despescar.hotelservice.dto.hotel.request.HotelRequest;
import com.despescar.hotelservice.dto.hotel.response.HotelResponse;
import com.despescar.hotelservice.service.HotelService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class HotelController {
    @Autowired
    private HotelService hotelService;
	
	@GetMapping("/test")
    public String test() {
        return "Hotel Service funcionando correctamente";

	}
    @PostMapping("/hoteles")
    public ResponseEntity<HotelResponse> createHotel(@Valid @RequestBody HotelRequest request) {
        HotelResponse response = hotelService.createHotel(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    @GetMapping("/hoteles")
    public ResponseEntity<List<HotelResponse>> getAllHotels() {
        List<HotelResponse> hotels = hotelService.getAllHotels();
        return ResponseEntity.ok(hotels);
    }
    @PutMapping("/hoteles/{id}")
    public ResponseEntity<HotelResponse> updateHotel(@PathVariable UUID id, @Valid @RequestBody HotelRequest request) {
        HotelResponse response = hotelService.updateHotel(id, request);
        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/hoteles/{id}")
    public ResponseEntity<Void> deleteHotel(@PathVariable UUID id) {
        hotelService.deleteHotel(id);
        return ResponseEntity.noContent().build();

    }
    /**
     * Buscar un hotel por ID (UUID)
     */
    @GetMapping("/hoteles/{id}")
    public ResponseEntity<HotelResponse> getHotelById(@PathVariable UUID id) {
        HotelResponse response = hotelService.getHotelById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Buscar/Filtrar hoteles por ciudad
     */
    @GetMapping("/hoteles/ciudad/{city}")
    public ResponseEntity<List<HotelResponse>> getHotelsByCity(@PathVariable String city) {
        List<HotelResponse> hotels = hotelService.getHotelsByCity(city);
        return ResponseEntity.ok(hotels);
    }



}
