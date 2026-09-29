package com.despescar.payment_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.despescar.payment_service.exception.InvalidWebhookSignatureException;

@Component
public class MercadoPagoWebhookSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String webhookSecret;

    public MercadoPagoWebhookSignatureValidator(
            @Value("${mercadopago.webhook.secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public void validate(
            String signatureHeader,
            String requestIdHeader,
            String queryDataId) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new InvalidWebhookSignatureException("Mercado Pago webhook secret is not configured.");
        }
        if (queryDataId == null || queryDataId.isBlank()) {
            throw new InvalidWebhookSignatureException("Missing Mercado Pago webhook data.id query parameter.");
        }

        SignatureParts signatureParts = parseSignature(signatureHeader);
        String manifest = buildManifest(signatureParts.timestamp(), requestIdHeader, queryDataId);
        String expectedSignature = sign(manifest);

        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signatureParts.signature().getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidWebhookSignatureException("Invalid Mercado Pago webhook signature.");
        }
    }

    private SignatureParts parseSignature(String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidWebhookSignatureException("Missing Mercado Pago webhook signature.");
        }

        String timestamp = null;
        String signature = null;

        for (String part : signatureHeader.split(",")) {
            String[] entry = part.split("=", 2);
            if (entry.length != 2) {
                continue;
            }
            String key = entry[0].trim();
            String value = entry[1].trim();
            if ("ts".equals(key)) {
                timestamp = value;
            }
            if ("v1".equals(key)) {
                signature = value;
            }
        }

        if (timestamp == null || signature == null) {
            throw new InvalidWebhookSignatureException("Malformed Mercado Pago webhook signature.");
        }

        return new SignatureParts(timestamp, signature);
    }

    private String buildManifest(
            String timestamp,
            String requestIdHeader,
            String queryDataId) {

        StringBuilder manifest = new StringBuilder();
        if (queryDataId != null && !queryDataId.isBlank()) {
            manifest.append("id:")
                    .append(queryDataId.toLowerCase())
                    .append(';');
        }
        if (requestIdHeader != null && !requestIdHeader.isBlank()) {
            manifest.append("request-id:")
                    .append(requestIdHeader)
                    .append(';');
        }
        manifest.append("ts:")
                .append(timestamp)
                .append(';');
        return manifest.toString();
    }

    private String sign(String manifest) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            Arrays.stream(toUnsignedInts(digest))
                    .forEach(value -> hex.append(String.format("%02x", value)));
            return hex.toString();
        } catch (Exception ex) {
            throw new InvalidWebhookSignatureException("Could not validate Mercado Pago webhook signature.");
        }
    }

    private int[] toUnsignedInts(byte[] digest) {
        int[] unsigned = new int[digest.length];
        for (int i = 0; i < digest.length; i++) {
            unsigned[i] = digest[i] & 0xff;
        }
        return unsigned;
    }

    private record SignatureParts(
            String timestamp,
            String signature) {
    }
}
