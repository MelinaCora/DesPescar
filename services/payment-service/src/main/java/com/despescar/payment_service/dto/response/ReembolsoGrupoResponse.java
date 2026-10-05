package com.despescar.payment_service.dto.response;

/** Resultado del reembolso de un grupo (contrato CB5). */
public record ReembolsoGrupoResponse(int reembolsados, int cancelados, int fallidos) {
}
