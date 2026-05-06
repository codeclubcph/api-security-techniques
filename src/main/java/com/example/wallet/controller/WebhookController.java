package com.example.wallet.controller;

import com.example.wallet.dto.WebhookPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    /**
     * POST /api/webhook/payment
     * ⚠️ VULNERABILITY: No HMAC signature verification — anyone can POST fake events.
     *    There is no X-Signature-256 header check at all.
     *    In Exercise 2, participants will add constant-time HMAC-SHA256 verification.
     */
    @PostMapping("/payment")
    public ResponseEntity<Map<String, String>> receivePaymentWebhook(
            @RequestHeader(value = "X-Signature-256", required = false) String signature,
            @RequestBody WebhookPayload payload) {

        // ⚠️ Signature header is received but never validated
        System.out.println("Received webhook event: " + payload.getEvent()
                + " for account " + payload.getAccountId());

        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "event", payload.getEvent()
        ));
    }
}
