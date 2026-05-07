package com.example.wallet;

import com.example.wallet.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security regression tests for Exercise 2.
 *
 * Run BEFORE fixing anything:  all tests marked ❌ should FAIL.
 * Run AFTER each fix:          the corresponding tests should go GREEN.
 *
 * ./gradlew test
 */
@SpringBootTest
@AutoConfigureMockMvc
@Disabled
class SecurityFixesTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @Value("${app.webhook.secret}")
    String webhookSecret;

    String aliceToken;

    @BeforeEach
    void setUp() {
        aliceToken = jwtUtil.generateToken("alice", "USER");
    }

    // ── Fix 1: IDOR — ownership checks ───────────────────────────────────────

    @Test
    @DisplayName("❌ Fix 1 – Alice cannot read Bob's account (expects 403)")
    void fix1_aliceCannotReadBobsAccount() throws Exception {
        mvc.perform(get("/api/accounts/2")
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("❌ Fix 1 – Alice cannot read Bob's profile (expects 403)")
    void fix1_aliceCannotReadBobsProfile() throws Exception {
        mvc.perform(get("/api/users/2")
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("❌ Fix 1 – Alice cannot read Bob's transactions (expects 403)")
    void fix1_aliceCannotReadBobsTransactions() throws Exception {
        mvc.perform(get("/api/transactions/account/2")
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    // ── Fix 2: Sensitive data — password not in response ─────────────────────

    @Test
    @DisplayName("❌ Fix 2 – Profile response must not contain password field")
    void fix2_profileDoesNotExposePassword() throws Exception {
        mvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // ── Fix 3: Security headers ───────────────────────────────────────────────

    @Test
    @DisplayName("❌ Fix 3 – Response must include HSTS, X-Frame-Options, X-Content-Type-Options")
    void fix3_securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/accounts")
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().exists("X-Frame-Options"))
                .andExpect(header().exists("X-Content-Type-Options"));
    }

    // ── Fix 4: CORS — allowlist only ──────────────────────────────────────────

    @Test
    @DisplayName("❌ Fix 4 – CORS preflight from evil-site.com must be rejected")
    void fix4_corsRejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/api/accounts")
                .header("Origin", "https://evil-site.com")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ── Fix 5: Webhook HMAC verification ─────────────────────────────────────

    @Test
    @DisplayName("❌ Fix 5 – Webhook must reject requests with no signature (expects 401)")
    void fix5_webhookRejectsRequestWithNoSignature() throws Exception {
        mvc.perform(post("/api/webhook/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"payment.completed\",\"accountId\":\"4\",\"amount\":\"99999.00\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("✅ Fix 5 – Webhook must accept requests with a valid HMAC signature")
    void fix5_webhookAcceptsValidHmacSignature() throws Exception {
        String body = "{\"event\":\"payment.completed\",\"accountId\":\"1\",\"amount\":\"500.00\"}";
        String signature = "sha256=" + computeHmac(body);

        mvc.perform(post("/api/webhook/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature-256", signature)
                .content(body))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String computeHmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes()));
    }
}
