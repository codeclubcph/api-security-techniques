package com.example.wallet.controller;

import com.example.wallet.dto.WebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    @Value("${app.webhook.secret}")
    private String webhookSecret;

    private final ObjectMapper objectMapper;

    @PostMapping("/payment")
    public ResponseEntity<Map<String, String>> receivePaymentWebhook(
            @RequestHeader(value = "X-Signature-256", required = false) String signature,
            @RequestBody String rawBody) {

        if (signature == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing signature");
        }
        verifySignature(signature, rawBody);

        WebhookPayload payload = parsePayload(rawBody);
        System.out.println("Verified webhook event: " + payload.getEvent()
                + " for account " + payload.getAccountId());

        return ResponseEntity.ok(Map.of("status", "accepted", "event", payload.getEvent()));
    }

    private void verifySignature(String received, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of()
                    .formatHex(mac.doFinal(body.getBytes()));
            if (!MessageDigest.isEqual(expected.getBytes(), received.getBytes())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC verification failed", e);
        }
    }

    private WebhookPayload parsePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload");
        }
    }
}
