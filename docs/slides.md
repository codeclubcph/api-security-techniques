---
marp: true
theme: default
paginate: true


  <!-- Title slide -->
# 🔐 API Security Techniques
### Why most APIs are fake secure — and what to do about it

  **Magdalena Furman**
  Senior Software Engineer

  <!--
SAY: "Welcome everyone — two quick questions, show of hands."

SAY: "Who has written or maintained a REST API?"

Almost everyone raises their hand.

SAY: "Who has had a security incident — or suspects they might have had one and just didn't know about it?"

A few hands, some nervous laughs. Use that gap — most people in the room have probably shipped a vulnerability. Today they'll know exactly what it looks like and how to stop it.

SAY: "Quick housekeeping before we start: Docker running? Repository cloned? Go ahead and run docker compose up now if you haven't — it takes about a minute."
-->

---

<style scoped>
table { font-size: 0.7em; }
</style>

## Agenda — 4 hours

| Time        | Block                                                 |
|-------------|-------------------------------------------------------|
| 0:00 – 0:10 | ✨ Introduction                                        |
| 0:10 – 0:40 | 🧠 Theory Block 1: Why Most APIs Are Fake Secure      |
| 0:40 – 0:45 | ☕ Break (5 min)                                       |
| 0:45 – 1:15 | 🛠 Exercise 1: Breaking Things                        |
| 1:15 – 1:20 | ☕ Break (5 min)                                       |
| 1:20 – 2:00 | 🧠 Theory Block 2: What Actually Breaks in Production |
| 2:00 – 2:20 | ☕ Break (20 min)                                      |
| 2:20 – 3:45 | 🛠 Exercise 2: Fixing Production Issues               |
| 3:45 – 3:50 | ☕ Break (5 min)                                       |
| 3:50 – 4:00 | 🎯 Key Takeaways                                      |

<!--
Walk through in under 60 seconds — don't read it line by line. Just land the structure.

SAY: "Two theory blocks, two exercises. The theory gives you vocabulary for what you're about to do hands-on. You'll recognise everything in the exercise because you just heard it. The exercises are the point."

Remind about Docker now if you haven't already. Anyone who hasn't pulled the repo needs to do it during the first theory block.
-->

---

<!-- ═══════════════════════════════════════════════════
     BLOCK 1 — THEORY
══════════════════════════════════════════════════════ -->

# 🧠 Block 1
## Why Most APIs Are Fake Secure

<!--
Pause briefly. Let people settle.

SAY: "Everything we cover in the next 30 minutes, you'll immediately prove in the exercise."
-->

---

## The core insight

> Security isn't a feature you add at the end —
> it's a foundation you build from day one.

Most applications still get it wrong.

**If auth, validation, and encryption are weak → nothing else matters.**

Three fundamentals. If any one is broken, your API is broken.

<!--
Read the quote on the slide out loud, then pause and let it sit.

SAY: "Who here has shipped something and called it 'secure' because it had a login page?"

A few hands usually go up; some nervous laughs. The point: having a login page and enforcing authentication on every resource are completely different things. Most teams do the former and assume it implies the latter. Today they'll see exactly why it doesn't.

Keep this slide to about 1 minute.
-->

---

## Fundamental #1 — Authentication & Authorization

**Authentication**: Who are you?
**Authorization**: Are you allowed to do this?

> These are not the same thing — and most bugs live in authorization.

### JWT — the production standard
- **Access token**: short-lived (15 min), stateless
- **Refresh token**: longer-lived, stored securely, rotated on use
- Never put sensitive data in the payload — it's base64, not encrypted

<!--
SAY: "Authentication and authorization are not the same thing — and most bugs live in authorization. Authentication is the bouncer checking your ID at the door. Authorization is checking if you're on the VIP list once you're inside."

The common failure: teams implement the JWT check and assume it means users can only access their own data. The JWT only proves who you are — it says nothing about what you're allowed to see.

If the API is running: take Alice's token from Postman, go to jwt.io, paste it — the payload decodes instantly without knowing the secret.

SAY: "Never put anything in the JWT payload you wouldn't put on a billboard."

SAY: "Let's look at exactly how this plays out in code."
-->

---

## JWT lifecycle done right

```
Client → POST /auth/login → { access_token, refresh_token }
Client → GET /api/data    → Authorization: Bearer <access_token>

access_token expires in 15 min ←
Client → POST /auth/refresh → new access_token
```

### What the vulnerable API does instead:
```yaml
# application.yml
app:
  jwt:
    secret: secret123        # ⚠️ 9 characters — trivially brute-forced
    expiration: 86400000     # ⚠️ 24 hours — no refresh token pattern
```

<!--
Trace the arrows on the diagram as you talk.

SAY: "Client logs in, gets two tokens. Uses the short-lived one for requests. When it expires, uses the refresh token to get a new access token."

SAY: "What happens if a token is stolen?"

Let someone answer. Then:

SAY: "With a 24-hour token and no refresh, the attacker has a full day and you have no way to invalidate it — it's stateless. With 15-minute access tokens and refresh rotation, a stolen token expires in 15 minutes. And if the refresh token is stolen and used, the rotation means the original holder's next refresh fails — you detect the theft."

SAY: "The vulnerable API's secret is 'secret123' — 9 characters, in plain text in application.yml. In Exercise 1, you'll crack it at jwt.io in about 10 seconds. How many of you have a JWT secret that's just a word or phrase?"

SAY: "That secret problem is a symptom of a deeper issue — let's look at the authorization mistake underneath it."
-->

---

## The #1 authorization mistake

```java
// ⚠️ BROKEN — Controller just passes the ID through
@GetMapping("/{id}")
public ResponseEntity<Account> getAccountById(@PathVariable Long id) {
    return ResponseEntity.ok(accountService.getAccountById(id));
}

// ⚠️ BROKEN — Service has no ownership check
public Account getAccountById(Long id) {
    return accountRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Not found"));
}
```

**IDOR — Insecure Direct Object Reference**
Alice logs in, changes `/api/accounts/1` to `/api/accounts/2` → sees Bob's balance.

> "If you don't validate ownership, your API is already broken."

<!--
IDOR is OWASP API Security Top 10 #1 — the most common real-world API vulnerability. Not theoretical. It's what attackers look for first.

Point at both code blocks together.

SAY: "The controller gets an ID from the URL and passes it to the service. The service calls findById and returns whatever it finds. Where does it check that the caller is allowed to see this account?"

Pause.

SAY: "It doesn't."

SAY: "How many of you have a findById call somewhere in your codebase without an ownership check right after it?"

Let the hands go up. Let the moment land.

Real world: the Optus data breach (Australia, 2022) involved sequential customer IDs in an API — classic IDOR. 9.8 million records. The fix is the same one we're about to write.
-->

---

## Authorization must be enforced in the service layer

```java
// ✅ CORRECT — service layer validates ownership
public Account getAccountById(Long id, String callerUsername) {
    Account account = accountRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Not found"));

    if (!account.getOwner().getUsername().equals(callerUsername)) {
        throw new AccessDeniedException("You don't own this account");
    }
    return account;
}
```

The controller just passes `authentication.getName()`. The service decides.

<!--
Read the code on the slide out loud.

SAY: "We accept a callerUsername, look up the account, compare owners, throw if they don't match. That's it."

SAY: "Why the service layer specifically? Because the controller is not the only caller. Scheduled jobs, async event listeners, internal service-to-service calls — none of them go through the controller. If your check is in the controller, those paths bypass it entirely. The service layer is the only place that always runs, no matter how it's invoked."

Testing note for later: this is also why testing the service in isolation is so valuable — the test proves the check is enforced without needing an HTTP request.

SAY: "Let's look at the other two fundamentals quickly, then you'll break all of this yourself."
-->

---

## Fundamental #2 — Input Validation

**Never. Trust. Input.**

Say it like a mantra. It applies to:
- Request bodies
- Path variables
- Query parameters
- Headers
- Webhook payloads

<!--
SAY: "Never. Trust. Input."

Say it twice. Then ask the room to say it with you once — sounds corny, sticks anyway.

The list is deliberate. Most developers think of request bodies. They forget path variables (IDOR lives here), query parameters (injection lives here), headers (forgeable), and webhook payloads.

SAY: "Real example: path traversal. A path variable used to build a file path, unsanitised. An attacker sends ../../etc/passwd as the ID. The server reads the system file and returns it. Simple, completely preventable, real."

SAY: "We also have a keyword search parameter in the vulnerable API that feeds directly into a SQL LIKE clause. You'll see that in Exercise 1, Challenge 3."
-->

---

## Validation in Spring Boot — more than @NotBlank

```java
// Weak — only checks presence
public record CreateTransactionRequest(
    @NotBlank String description,
    BigDecimal amount
) {}

// Strong — validates meaning
public record CreateTransactionRequest(
    @NotBlank @Size(max = 255)
    @Pattern(regexp = "^[\\w\\s.,!?-]+$", message = "Invalid characters")
    String description,

    @NotNull @Positive @DecimalMax("10000.00")
    BigDecimal amount
) {}
```

Custom validators for business rules that annotations can't express.

<!--
Walk through the contrast on the slide.

SAY: "@NotBlank only checks that the string isn't empty — it says nothing about what's in it."

SAY: "@Pattern rejects anything that isn't alphanumeric or standard punctuation. That single annotation blocks SQL injection characters, script tags, and path traversal attempts before they reach your service."

SAY: "What happens if someone sends amount=999999999? If there's no cap, a malicious transfer request could overflow balance fields. One annotation prevents it."

Bean Validation is for structural rules — format, length, range. Business rules like "you can't transfer more than your balance" need explicit checks in the service layer.

Keep this to 2 minutes — it's vocabulary for the exercise, not the main event.
-->

---

## Sensitive data leakage — the silent vulnerability

```json
// ⚠️ What GET /api/users/me returns in the vulnerable API:
{
  "id": 1,
  "username": "alice",
  "email": "alice@example.com",
  "password": "password123",    // ← plain-text password in the response
  "role": "USER"
}
```

Never return: passwords (even hashed), internal IDs from other systems,
raw stack traces, database error messages, or internal role flags.

<!--
Read the JSON on the slide out loud. Pause on "password": "password123".

SAY: "That is the actual API response. Alice just fetched her own profile and got her plain-text password back in the JSON."

Let the room react. This happens more than you'd think — a developer adds a field to debug, the PR reviewer misses it, it ships, stays there for months, then someone curls the endpoint and writes it up on Twitter.

SAY: "The pattern that prevents this: never return your JPA entity directly from a controller. Always map to a dedicated response DTO. The DTO is an explicit allowlist of what you're willing to share. If a field isn't in the DTO, it can't leak."

Real world: the Peloton API (2021) returned private user data — age, gender, city, workout history — to any authenticated user, no matter whose account it was. Classic DTO plus IDOR combination.
-->

---

## Fundamental #3 — Encryption

> "This is table stakes — not advanced security."

| What | Wrong | Right |
|------|-------|-------|
| Passwords | Plain text / MD5 | **bcrypt / argon2** |
| Secrets in config | Hardcoded strings | **Vault / env vars / KMS** |
| Data in transit | HTTP | **HTTPS everywhere** |
| Data at rest | Plaintext | **AES-256** |

```java
// ✅ Password encoding in Spring Security
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12); // cost factor 12
}
```

<!--
SAY: "Table stakes. Not advanced. Not optional. The minimum bar for any production system."

SAY: "BCrypt cost factor 12 takes about 300 milliseconds to hash on modern hardware. Doesn't that make login slow?"

Pause.

SAY: "300ms is imperceptible to a human. But for an attacker trying a billion passwords offline, that's 300ms times a billion — over 9 years. The slowness is the whole point."

SAY: "MD5 and SHA-1 are instant — a modern GPU can test billions per second. A leaked MD5 database is cracked in hours. BCrypt-12 takes years on the same hardware."

SAY: "And never commit a secret to git — not even temporarily, not even in a private repo."

The vulnerable API has `secret: secret123` in `application.yml` — committed, hardcoded, 9 characters. Participants crack it in Exercise 1 in about 10 seconds.
-->

---

## Block 1 — Summary

**Layer 1: Don't get hacked instantly**

✅ Short-lived JWT tokens (15 min) + refresh pattern
✅ Authorization enforced in the **service layer**, not the controller
✅ Validate input — type, range, pattern, and business rules
✅ Never return sensitive fields — use dedicated response DTOs
✅ Hash passwords with bcrypt/argon2
✅ HTTPS everywhere, secrets in vault

<!--
SAY: "For each line — hands up if your current production API does this."

Go through slowly. Watch for the lines where hands drop. Common gaps: short-lived JWTs (most APIs use 24h), ownership checks on every findById, response DTOs instead of raw entities.

SAY: "The API you're about to break is missing all six of these. Your job is to find exactly where each one is missing."

Move quickly to the break — energy is high, don't let it drop.
-->

---

# ☕ Break — 5 minutes

> "You now have the vocabulary. Time to see it in the wild."

See you back in 5.

<!--
Keep it tight. People are usually energised going into the first exercise.

SAY: "See you back in 5."
-->

---

<!-- ═══════════════════════════════════════════════════
     EXERCISE 1
══════════════════════════════════════════════════════ -->

# 🛠 Exercise 1
## Breaking Things

**⏱ 30 minutes**
Open `docs/exercise-1-breaking-things.md`

<!--
Before releasing the room: do two requests live on the projector — takes 60 seconds and sets the tone.
1. Run "Login as Alice" → show the token auto-saving in the collection variables panel.
2. Run "🔥 IDOR – Alice reads Bob's account (id=2)" → show Bob's balance appearing.

SAY: "Alice is logged in. She changed one digit in the URL. That's it."

Pause.

SAY: "Your turn. Open exercise-1-breaking-things.md and work through the challenges in order."

Facilitation notes:
- Challenges 1–3 (IDOR) go quickly — most people finish in 10 minutes. Those are the ones that land the main point.
- Challenge 4 (brute force) is slower — point them toward Postman Runner if they want to automate it.
- Challenge 5 (fake webhook) is usually the most surprising one.
- Challenges 6 and 7 are bonus for fast finishers.
- Walk the room. Ask "what did you find?" rather than giving answers.
-->

---

# ☕ Break — 5 minutes

> "Everything you just broke is because one of
>  those 3 fundamentals was missing."

See you back in 5.

<!--
Say this before people stand up — it reframes what they just did.

SAY: "Everything you just broke is because one of those 3 fundamentals was missing."

Short break — energy is high after the exercise, carry it into the debrief.
-->

---

## 🎯 Debrief — What did we break?

| Vulnerability                                | Root cause |
|----------------------------------------------|-----------|
| IDOR – User profiles, Accounts, Transactions | No ownership check in service layer |
| Sensitive data leak                          | Password field in response DTO |
| Brute-force login                            | No rate limiting on auth endpoint |
| Fake webhook                                 | No HMAC signature verification |
| Exposed actuator                             | `permitAll()` on `/actuator/**` |
| Weak JWT secret                              | 9-char hardcoded secret, crackable offline |
| H2 console exposed                           | `permitAll()` on `/h2-console/**` — full DB via browser |

<!--
Walk the table row by row — one sentence each, fast. You're not explaining the vulnerabilities again, you're naming them collectively so the pattern is visible.

SAY: "Look at the root cause column. Missing check. Missing check. Missing check. No limiting. No verification. No auth. No auth. Weak secret. No auth."

SAY: "None of these required a sophisticated attack. No zero-days. No exploit frameworks. Just knowing where to look and trying the obvious thing."

Pause. Let it sink in. Then move to Block 2.
-->

---

# ☕ Break — 20 minutes

> "You've broken it. After the break — you fix it."

<!--
Longest break — intentional. People need to recharge before the 85-minute exercise. Use this time to check that everyone has the repo open and can run `./gradlew test`.
-->

---

<!-- ═══════════════════════════════════════════════════
     BLOCK 2 — THEORY
══════════════════════════════════════════════════════ -->

# 🧠 Block 2
## What Actually Breaks in Production

<!--
Energy is usually lower coming back from the long break. Don't ease in — open with a strong line before clicking to the first slide.

SAY: "You just broke nine things in a running API. All of them without admin access. All of them in under 30 minutes. Block 2 is about making sure that never happens in your system."
-->

---

## The uncomfortable truth

> "Your API can be 'correct' and still get destroyed in production."

Authentication, validation, and encryption protect your **logic**.

But production traffic is different:
- Scrapers that ignore rate limits
- Bots that rotate IPs and probe endpoints 24/7
- Browsers executing injected scripts from compromised CDNs
- Partners sending forged webhook payloads

The next 4 techniques stop attacks **before your code even runs**.

<!--
Pause on the bullet list.

SAY: "Has anyone here dealt with a scraper? A bot hammering a login endpoint? A partner that sent a webhook payload that triggered something it shouldn't have?"

Let someone share briefly — it makes the next slides feel like solutions to real problems rather than theory.

SAY: "Block 1 was about protecting your business logic — auth, validation, encryption. Block 2 is about surviving the internet. Adversarial traffic that doesn't care about your logic at all."

SAY: "The techniques in this block stop attacks before your code even runs. Not better code — a different layer of defence."
-->

---

## Production issue #1 — Security Headers

**The cheapest protection most teams ignore.**

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Content-Security-Policy: default-src 'self'
Referrer-Policy: strict-origin-when-cross-origin
```

### What the vulnerable API returns:
```bash
curl -I http://localhost:8080/api/accounts
# HTTP/1.1 200 OK
# Content-Type: application/json
# (no security headers)
```

These stop entire attack classes **before your route handler is called**.

<!--
If the API is running locally: run the curl command live — no headers appear. Then:

SAY: "This is what Exercise 2 will fix."

Quick explanations as you point at each header on the slide:
- HSTS tells browsers to only use HTTPS — prevents SSL stripping.
- X-Frame-Options: DENY prevents clickjacking — your app embedded in an iframe on a malicious site.
- X-Content-Type-Options: nosniff stops browsers guessing content types.
- CSP tells browsers which scripts are allowed — stops injected scripts from compromised CDNs.

SAY: "This is 5 lines of Spring Security config. The cost-to-benefit ratio is enormous."
-->

---

<style scoped>
table { font-size: 0.7em; }
</style>

## Production issue #2 — Rate Limiting 🔥

> "If you don't rate-limit your login endpoint,
>  you don't have authentication."

A 6-character lowercase password has 308 million combinations.
At 100 req/s — cracked in 35 minutes.

### Endpoint-specific limits (not global)

| Endpoint | Limit | Why |
|----------|-------|-----|
| `POST /auth/login` | 5 / min per IP | Brute force protection |
| `POST /auth/register` | 3 / hour per IP | Bot/spam protection |
| `GET /api/accounts` | 100 / min | Normal usage |
| `GET /actuator/**` | 10 / min | Monitoring only |

<!--
SAY: "26 to the power of 6 — who knows the answer?"

Let someone shout it out. Then:

SAY: "308 million combinations. At 100 requests per second — that's 35 minutes for exhaustive search. A dictionary of the 10,000 most common passwords at 100 req/s? 100 seconds."

SAY: "How hard is 100 req/s? You can do that with a curl loop on a laptop. A cheap VPS gets you 10,000."

Endpoint-specific is the critical detail. A global rate limit of 1000 req/min doesn't help if all 1000 can be login attempts from the same IP.

SAY: "Does anyone know right now, without checking, whether their login endpoint is rate limited?"

Wait. Usually silence, maybe one hand.

SAY: "That's the answer to 'are we at risk.'"

We use Bucket4j in the exercise — 5 attempts per minute per IP.
-->

---

## Production issue #3 — CORS Misconfiguration

```java
// ⚠️ VULNERABLE — wildcard + credentials = broken
config.setAllowedOriginPatterns(List.of("*"));
config.setAllowCredentials(true);
```

This means: any website in any browser can make authenticated
requests to your API using the logged-in user's cookies.

```java
// ✅ CORRECT
config.setAllowedOrigins(List.of(
    "https://yourapp.com",
    "https://staging.yourapp.com"
));
config.setAllowCredentials(true);
```

**Never use `*` in production with credentials.**

<!--
SAY: "The sneaky part: Postman ignores CORS entirely — it's a browser mechanism. So you won't catch this in testing."

SAY: "It only bites you when a browser from evil-site.com makes a credentialed request to your API. The browser sends the user's cookies, your API accepts it because you allow all origins, and the attacker's site reads the response."

SAY: "Real scenario: a user is logged into your app, they visit evil-site.com, that page makes a fetch to your API. With wildcard CORS and credentials enabled, it succeeds. The attacker's page has just read your user's data."
-->

---

## Production issue #4 — Webhook HMAC Verification

Without verification, anyone can POST to your webhook endpoint:

```json
POST /api/webhook/payment
{
  "event": "payment.completed",
  "accountId": "4",
  "amount": "99999.00"
}
```

### The fix — HMAC-SHA256 + constant-time comparison

```java
String expected = "sha256=" + computeHmac(secret, rawBody);

// ⚠️ Wrong — timing attack vulnerable
if (!expected.equals(receivedSignature)) { ... }

// ✅ Right — constant-time comparison
if (!MessageDigest.isEqual(expected.getBytes(), receivedSignature.getBytes())) { ... }
```

<!--
Slow down for this one — it's the most surprising explanation of the day.

SAY: "String.equals() short-circuits — it returns false the moment it finds a non-matching character. So comparing two signatures that differ in the first byte returns faster than two that differ in the last byte. An attacker can measure microsecond differences over thousands of requests and deduce the correct signature one character at a time."

SAY: "MessageDigest.isEqual() always compares every byte, regardless of where the mismatch is. Constant time. GitHub, Stripe, and every major webhook provider use exactly this pattern."
-->

---

## Block 2 — Summary

**Layer 2: Survive real traffic**

✅ Security headers on every response (Spring Security `headers()`)
✅ Rate limiting per-endpoint, per-IP (Bucket4j / Spring filters)
✅ Explicit CORS allowlist — no wildcards with credentials
✅ HMAC-SHA256 webhook verification with constant-time comparison
✅ Monitor and alert on anomalies — you need to know when it breaks

<!--
SAY: "For each of these — hands up if your current production API has it."

Go slowly. Rate limiting and security headers are usually the two where almost no hands go up.

SAY: "You just broke this API in Exercise 1. Now you're the engineer who got paged at 2am because someone else did the same thing. Exercise 2 is your incident response."
-->

---

<!-- ═══════════════════════════════════════════════════
     EXERCISE 2
══════════════════════════════════════════════════════ -->

# 🛠 Exercise 2
## Fixing Production Issues

**⏱ 85 minutes** — You're now the engineer after the incident.
Open `docs/exercise-2-fixing-production.md`

<!--
SAY: "After each fix, run ./gradlew test. You'll see the test go from red to green. That's your verification. If it's green, it works — no need to rebuild Docker just to check."

Facilitation notes:
- Fix 1 (25 min): The IDOR fix is the core. If someone is stuck after 10 minutes, walk through 1a on the projector, then let them do 1b and 1c independently — the pattern is identical.
- Fix 2 (5 min): Easy win. Nudge early finishers here for momentum.
- Fix 3 (10 min): The most common stumble is missing imports — remind the room to check Step 1 before the code block.
- Fixes 4–8: "if time." Fast groups get through all 8. Don't stress if slower groups only reach Fix 3 — they've fixed the most critical vulnerabilities.
- Pair faster and slower participants in the last 20 minutes.
- Fix 5 (HMAC): If the room reaches it, pause and walk through the timing attack — it's the most memorable moment of the day.
-->

---

<style scoped>
table { font-size: 0.7em; }
</style>

## 🎯 Debrief — What we fixed

| Fix | Technique | Impact |
|-----|-----------|--------|
| Ownership checks | Authorization in service layer | Stops IDOR attacks |
| Remove password from DTO | Sensitive data control | Stops credential leakage |
| Security headers | HTTP response hardening | Stops clickjacking, MIME sniffing |
| CORS allowlist | Explicit origin control | Stops cross-origin data theft |
| Webhook HMAC | Constant-time verification | Stops forged payment events |
| BCrypt hashing | Slow, salted password hash | Stops offline password cracking |
| Actuator lockdown | Require auth on sensitive endpoints | Stops runtime fingerprinting |
| Rate limiting | Token bucket per IP | Stops brute-force login attacks |

**Every fix here is a production-ready pattern used in real systems.**
Not theoretical. Not "best practices." Things that prevent real incidents.

<!--
Walk the table fast — one sentence per row.

If the room hasn't reached BCrypt or rate limiting:

SAY: "These last two — BCrypt and rate limiting — are the ones to take home and implement on Monday. Small changes, outsized impact."

Then read the last line of the slide out loud and pause.

SAY: "Every fix here is a production-ready pattern used in real systems. Not theoretical. Not best practices. Things that prevent real incidents."

Pause. Don't rush it. Then move to the break.
-->

---

# ☕ Break — 5 minutes

> "Last one. Then we wrap up."

Back in 5.

<!--
SAY: "Last one. Back in 5."

Use this time to pull up the closing slides and prepare for questions.
-->

---

<!-- ═══════════════════════════════════════════════════
     CLOSING
══════════════════════════════════════════════════════ -->

# 🎯 Key Takeaways

---

## Layer 1 — Don't get hacked instantly

These are non-negotiable. If any one is missing, the API is broken.

| | |
|--|--|
| 🔐 | **Auth** — short-lived JWT, refresh token rotation |
| ✅ | **Validation** — type, range, pattern, ownership |
| 🔒 | **Encryption** — bcrypt, HTTPS, vault for secrets |

<!--
Read each row slowly, with a line after each.

After Auth: SAY: "Not just 'does a token exist' — does this token belong to someone who's allowed to do this specific thing?"

After Validation: SAY: "Every endpoint. Every input. Not just the ones that feel risky."

After Encryption: SAY: "Not optional. Not 'we'll add it later.' This is the floor."

SAY: "You can ship without Layer 2 and be okay for a while. You cannot ship without Layer 1 and call it production-ready."
-->

---

## Layer 2 — Survive real traffic

These protect you when your code is correct but the world isn't.

| | |
|--|--|
| 🛡️ | **Rate Limiting** — per-endpoint, per-IP |
| 🪟 | **Security Headers** — stop attacks before your code runs |
| 🌐 | **CORS** — explicit allowlist, never wildcard with credentials |
| 🔗 | **Webhook HMAC** — constant-time verification |

<!--
SAY: "Your code can be perfect and you can still be exploited — because the threat isn't inside your logic, it's in the traffic pattern."

Rate limiting stops brute force before auth runs. Security headers stop attack classes at the browser. CORS stops cross-origin data theft. HMAC stops forged events at the boundary. All four before your code runs — that's the point of this layer.

SAY: "Both layers together — that's what a production-ready API looks like."
-->

---

## The reality check

> Most attacks are not sophisticated.
> They exploit basic gaps — at scale.

The attacker doesn't need to break your encryption.
They just need to find one endpoint without an ownership check.
One login route without rate limiting.
One CORS policy that allows any origin.

**You don't need perfect security. You need no obvious gaps.**

<!--
Read the slide slowly. Pause after "at scale."

SAY: "Most people imagine sophisticated hackers. The reality is scripts probing millions of APIs looking for these exact gaps. Automated, opportunistic, at scale."

SAY: "This is why fixing the basics matters more than any advanced technique. You don't need perfect security. You need no obvious gaps."
-->

---

## What to do on Monday

1. **Audit your JWTs** — what's the expiry? is there a refresh token?
2. **Search for `findById`** — is every one followed by an ownership check?
3. **Check your login endpoint** — is it rate limited?
4. **Inspect your response headers** — run `curl -I` on your API
5. **Review your CORS config** — is `*` used anywhere with credentials?
6. **Find your webhooks** — is every one verifying the signature?

<!--
SAY: "Which of these will you check first? Turn to the person next to you and say it out loud."

Give 30 seconds. Then ask 2–3 people to share. Creates commitment, ends on energy.

The findById search is particularly powerful — people can Ctrl+F their codebase right now on their laptop.
-->

---

## Thank you 🙏

**Articles that inspired this course:**
- [Security Practices That Actually Protect Production Applications (Part 1)](https://medium.com/@madzia912/security-practices-that-actually-protect-production-applications-part-1-ebbe25f031d3)
- [What Actually Breaks in Production (Part 2)](https://medium.com/@madzia912/security-practices-that-actually-protect-production-apps-after-theyve-been-attacked-ce9a13363da3)

**Source code:** `https://github.com/codeclubcph/api-security-techniques`

> Questions?

<!--
Leave at least 10 minutes for questions. If the room is quiet:

SAY: "What was the most surprising thing you broke today?"

Common questions to be ready for:
- "How do I convince my team to prioritise this?" → SAY: "Frame it as risk, not best practice. IDOR is OWASP number 1 for a reason — it's what breaches actually look like."
- "What about OAuth2 / Keycloak?" → SAY: "Great for delegated auth. The ownership-check problem still exists behind it — it tells you who the user is, not what they're allowed to see."
- "Should we use an API Gateway for rate limiting?" → SAY: "Yes — and at the app level too. Defense in depth. If the gateway is misconfigured, the app catches it."
- "What about GraphQL / gRPC?" → SAY: "Same principles apply. IDOR doesn't care about your protocol."
-->