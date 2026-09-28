package com.despescar.payment_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MercadoPagoWebhookRequest {

    private String type;

    private Data data;

    @Getter
    @Setter
    public static class Data {

        private String id;
    }
}

