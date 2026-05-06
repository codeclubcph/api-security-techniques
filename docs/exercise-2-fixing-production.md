# 🛠 Exercise 2 — Fixing Production Issues

> **Goal:** Apply 5 concrete fixes to the vulnerable API.
> Each fix maps directly to a technique from Block 2 theory.
> Open the API source in your editor — you'll be editing real Java files.

---

## Setup

- [ ] Open the `src/` folder in your IDE or editor
- [ ] Run the test suite — you should see **7 tests failing**:
  ```bash
  ./gradlew test
  ```
- [ ] Your goal: make all tests green. The tests don't need Docker — they run against an in-memory database directly.
- [ ] Use Postman + `docker compose up --build` to manually verify after each fix if you want to see it live

---

## Fix 1 — Add Ownership Checks (IDOR) (15 min)

**File:** `api/src/main/java/com/example/wallet/service/AccountService.java`

### The bug:
```java
public Account getAccountById(Long id) {
    return accountRepository.findById(id)   // ← no ownership check
        .orElseThrow(() -> new RuntimeException("Account not found"));
}
```

### The fix — add a `callerUsername` parameter:
```java
public Account getAccountById(Long id, String callerUsername) {
    AppUser caller = userRepository.findByUsername(callerUsername)
            .orElseThrow(() -> new RuntimeException("User not found"));

    Account account = accountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found"));

    if (!account.getOwner().getId().equals(caller.getId())) {
        throw new org.springframework.security.access.AccessDeniedException(
                "You don't own this account");
    }
    return account;
}
```

**Also update the controller** to pass `auth.getName()`:
```java
// AccountController.java
@GetMapping("/{id}")
public ResponseEntity<Account> getAccountById(
        @PathVariable Long id, Authentication auth) {
    return ResponseEntity.ok(accountService.getAccountById(id, auth.getName()));
}
```

**Apply the same pattern** to `UserService.getUserById()` and `TransactionService`.

**Verify with tests:**
```bash
./gradlew test --tests "*.fix1_*"
```
All 3 `fix1_` tests should go green ✅

**Verify with Postman:** Re-run **"🔥 IDOR – Alice reads Bob's account (id=2)"** → `403 Forbidden`.

---

## Fix 2 — Remove Sensitive Fields from Responses (5 min)

**File:** `api/src/main/java/com/example/wallet/dto/UserResponse.java`

### The bug:
```java
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String password;  // ← this should never be here
    private String role;
}
```

### The fix — remove the password field entirely:
```java
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    // password removed
}
```

**Verify with tests:**
```bash
./gradlew test --tests "*.fix2_*"
```
**Verify with Postman:** Run **"My Profile (Alice – OK)"** → `password` field is gone from the response.

---

## Fix 3 — Add Security Headers (10 min)

**File:** `api/src/main/java/com/example/wallet/config/SecurityConfig.java`

### The bug:
```java
.headers(headers -> headers.disable())  // ← all headers stripped
```

### The fix:
```java
.headers(headers -> headers
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000))
    .frameOptions(frame -> frame.deny())
    .contentTypeOptions(Customizer.withDefaults())
    .referrerPolicy(referrer -> referrer
        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy
                .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
)
```

**Verify with tests:**
```bash
./gradlew test --tests "*.fix3_*"
```
**Verify with Postman:** Run **"Check Security Headers (after fix)"** → all 3 Postman tests pass ✅

---

## Fix 4 — Fix CORS to Allowlist Only (10 min)

**File:** `api/src/main/java/com/example/wallet/config/SecurityConfig.java`

### The bug:
```java
config.setAllowedOriginPatterns(List.of("*"));  // ← any origin
config.setAllowCredentials(true);               // ← with credentials = broken
```

### The fix:
```java
config.setAllowedOrigins(List.of(
    "http://localhost:3000",          // local dev frontend
    "https://yourapp.com"            // production frontend
));
config.setAllowCredentials(true);
```

**Verify with tests:**
```bash
./gradlew test --tests "*.fix4_*"
```
**Verify with Postman:** Run **"CORS Preflight from evil-site.com"** → no `Access-Control-Allow-Origin` header.

---

## Fix 5 — Webhook HMAC Verification (20 min)

**File:** `api/src/main/java/com/example/wallet/controller/WebhookController.java`

### The bug — signature header is ignored:
```java
@PostMapping("/payment")
public ResponseEntity<?> receivePaymentWebhook(
        @RequestHeader(value = "X-Signature-256", required = false) String signature,
        @RequestBody WebhookPayload payload) {
    // signature is never checked
}
```

### The fix — add a verification method and call it:
```java
@PostMapping("/payment")
public ResponseEntity<?> receivePaymentWebhook(
        @RequestHeader(value = "X-Signature-256") String signature,
        @RequestBody String rawBody) {

    verifySignature(signature, rawBody);

    WebhookPayload payload = parsePayload(rawBody);
    // process payload...
    return ResponseEntity.ok(Map.of("status", "accepted"));
}

private void verifySignature(String received, String body) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256"));
        String expected = "sha256=" + HexFormat.of()
                .formatHex(mac.doFinal(body.getBytes()));

        // ⚠️ Critical: use constant-time comparison to prevent timing attacks
        if (!MessageDigest.isEqual(expected.getBytes(), received.getBytes())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
        }
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
        throw new RuntimeException("HMAC verification failed", e);
    }
}
```

**Verify with tests:**
```bash
./gradlew test --tests "*.fix5_*"
```
Both `fix5_` tests should go green: one confirms unsigned requests are rejected, one confirms a correctly signed request is accepted.

**Verify with Postman:** Run **"🔥 Fake Webhook (no signature)"** → `401 Unauthorized`.

---

## 🎯 Debrief — What we fixed

| Fix | Technique | Impact |
|-----|-----------|--------|
| Ownership checks | Authorization in service layer | Stops IDOR attacks |
| Remove password from DTO | Sensitive data control | Stops credential leakage |
| Security headers | HTTP response hardening | Stops clickjacking, MIME sniffing |
| CORS allowlist | Explicit origin control | Stops cross-site request forgery |
| Webhook HMAC | Constant-time verification | Stops forged payment events |

**All 5 fixes are production-ready patterns used in real systems.**
Not theoretical. Not "best practices." Things that prevent real incidents.
