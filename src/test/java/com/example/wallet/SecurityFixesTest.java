package com.example.wallet;

import com.example.wallet.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
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

    @Test
    @DisplayName("❌ Fix 1 – Alice cannot search Bob's transactions (expects 403)")
    void fix1_aliceCannotSearchBobsTransactions() throws Exception {
        mvc.perform(get("/api/transactions/account/2/search")
                .param("keyword", "coffee")
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("✅ Fix 1 – Alice can search her own transactions")
    void fix1_aliceCanSearchOwnTransactions() throws Exception {
        mvc.perform(get("/api/transactions/account/1/search")
                .param("keyword", "coffee")
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());
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

    // ── Fix 6: BCrypt password hashing ───────────────────────────────────────

    @Test
    @DisplayName("❌ Fix 6 – Correct credentials must return 200 and a token")
    void fix6_loginWithCorrectCredentialsReturnsToken() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("❌ Fix 6 – Wrong password must return 401, not 500")
    void fix6_loginWithWrongPasswordReturns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("❌ Fix 6 – Unknown username must return 401, not 500")
    void fix6_loginWithUnknownUserReturns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nobody\",\"password\":\"anything\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Fix 3: Security headers ───────────────────────────────────────────────

    @Test
    @DisplayName("❌ Fix 3 – Response must include HSTS, X-Frame-Options, X-Content-Type-Options")
    void fix3_securityHeadersArePresent() throws Exception {
        // secure(true) is required: Spring Security only writes HSTS on HTTPS requests
        mvc.perform(get("/api/accounts")
                .secure(true)
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

    // ── Fix 7: Actuator locked down ───────────────────────────────────────────

    @Test
    @DisplayName("❌ Fix 7 – /actuator/metrics must require authentication (expects 401)")
    void fix7_actuatorRequiresAuthentication() throws Exception {
        mvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("✅ Fix 7 – /actuator/health remains public")
    void fix7_actuatorHealthRemainsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // ── Fix 8: Rate limiting on login ─────────────────────────────────────────

    @Test
    @DisplayName("❌ Fix 8 – Login must be rate-limited to 5 attempts per minute (expects 429)")
    void fix8_loginIsRateLimited() throws Exception {
        // Use a dedicated IP so this test has its own bucket, isolated from other tests
        String testIp = "10.99.0.1";
        String body = "{\"username\":\"alice\",\"password\":\"wrongpassword\"}";

        // First 5 attempts: wrong credentials but not yet rate-limited → 401
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", testIp)
                    .content(body))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt: bucket exhausted → 429 Too Many Requests
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", testIp)
                .content(body))
                .andExpect(status().isTooManyRequests());
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String computeHmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes()));
    }
}
