package com.despescar.hotelservice.controller;

import com.despescar.hotelservice.dto.request.HotelRequest;
import com.despescar.hotelservice.dto.response.DestinoResponse;
import com.despescar.hotelservice.dto.response.HotelDetalleResponse;
import com.despescar.hotelservice.dto.response.HotelResumenResponse;
import com.despescar.hotelservice.service.HotelAdminService;
import com.despescar.hotelservice.service.HotelCatalogoService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hoteles")
public class HotelController {

    private final HotelCatalogoService catalogo;
    private final HotelAdminService admin;

    public HotelController(HotelCatalogoService catalogo, HotelAdminService admin) {
        this.catalogo = catalogo;
        this.admin = admin;
    }

    @GetMapping
    public List<HotelResumenResponse> buscar(
            @RequestParam(required = false) String destino,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer huespedes) {
        return catalogo.buscar(destino, checkIn, checkOut, huespedes);
    }

    @GetMapping("/destinos")
    public List<DestinoResponse> destinos() {
        return catalogo.destinos();
    }

    @GetMapping("/{id}")
    public HotelDetalleResponse detalle(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer huespedes) {
        return catalogo.detalle(id, checkIn, checkOut, huespedes);
    }

    @PostMapping
    public ResponseEntity<HotelDetalleResponse> crear(@Valid @RequestBody HotelRequest request) {
        return new ResponseEntity<>(admin.crear(request), HttpStatus.CREATED);
    }
}
