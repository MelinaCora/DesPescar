package com.despescar.packageservice.controller;

import com.despescar.packageservice.dto.request.TravelPackageRequest;
import com.despescar.packageservice.dto.response.TravelPackageResponse;
import com.despescar.packageservice.exception.GlobalExceptionHandler;
import com.despescar.packageservice.service.TourPackageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PackageControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TourPackageService tourPackageService = Mockito.mock(TourPackageService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new PackageController(tourPackageService))
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreatePackage() throws Exception {
        TravelPackageResponse response = sampleResponse(1L, true);
        when(tourPackageService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Pack Iguazu"));

        verify(tourPackageService).create(any());
    }

    @Test
    void shouldListPackagesWithFilters() throws Exception {
        when(tourPackageService.search(eq("Patagonia"), eq(true), any(), any()))
                .thenReturn(List.of(sampleResponse(2L, true)));

        mockMvc.perform(get("/api/packages")
                        .param("destination", "Patagonia")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].destination").value("Patagonia"));

        verify(tourPackageService).search(eq("Patagonia"), eq(true), any(), any());
    }

    @Test
    void shouldGetPackageById() throws Exception {
        when(tourPackageService.findById(1L)).thenReturn(sampleResponse(1L, true));

        mockMvc.perform(get("/api/packages/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(tourPackageService).findById(1L);
    }

    @Test
    void shouldUpdatePackage() throws Exception {
        when(tourPackageService.update(anyLong(), any())).thenReturn(sampleResponse(1L, true));

        mockMvc.perform(put("/api/packages/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pack Iguazu"));

        verify(tourPackageService).update(anyLong(), any());
    }

    @Test
    void shouldDeactivatePackage() throws Exception {
        mockMvc.perform(delete("/api/packages/1"))
                .andExpect(status().isNoContent());

        verify(tourPackageService).deactivate(1L);
    }

    @Test
    void shouldActivatePackage() throws Exception {
        when(tourPackageService.activate(1L)).thenReturn(sampleResponse(1L, true));

        mockMvc.perform(post("/api/packages/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        verify(tourPackageService).activate(1L);
    }

    @Test
    void shouldRejectInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"sin nombre\"}"))
                .andExpect(status().isBadRequest());
    }

    private TravelPackageResponse sampleResponse(Long id, boolean active) {
        return TravelPackageResponse.builder()
                .id(id)
                .name("Pack Iguazu")
                .description("Un paquete turistico completo")
                .destination("Patagonia")
                .flightNumber("DP100")
                .hotelId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .durationNights(4)
                .basePrice(BigDecimal.valueOf(250000))
                .active(active)
                .createdAt(null)
                .updatedAt(null)
                .build();
    }

    private TravelPackageRequest sampleRequest() {
        return TravelPackageRequest.builder()
                .name("Pack Iguazu")
                .description("Un paquete turistico completo")
                .destination("Patagonia")
                .flightNumber("DP100")
                .hotelId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .durationNights(4)
                .basePrice(BigDecimal.valueOf(250000))
                .build();
    }
}
