package com.despescar.hotelservice.exception;

import java.util.UUID;

public class HotelNotFoundException extends RuntimeException {
    public HotelNotFoundException(UUID id) {
        super("Hotel not found with ID: " + id);
    }

    public  HotelNotFoundException(String message) {
        super(message);
    }
}
