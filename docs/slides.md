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
Welcome everyone. Quick question to start: show of hands — who has written or maintained a REST API?
Who has had a security incident — or suspects they might have had one and didn't know?
Today you'll break a real API, then fix it. This isn't a slides-only session.
-->

---

## About Me

**Magdalena Furman** — Senior Software Engineer

- Building production backend systems for 10+ years
- Wrote the articles this course is based on — links at the end
- Passionate about security that's **practical**, not theoretical

**What this course is NOT:**
- A compliance checklist
- A "never do X" scare session

**What it IS:**
- Patterns that stop real attacks in production systems

<!--
Keep this short — 2 minutes max. People are here for the content, not the bio.
Mention the Medium articles briefly — it builds credibility and they can read deeper after.
-->

---

## Agenda — 4 hours

| Time        | Block                                                |
|-------------|------------------------------------------------------|
| 0:00 – 0:15 | Introduction                                         |
| 0:15 – 0:45 | 🧠 Theory Block 1: Why Most APIs Are Fake Secure     |
| 0:45 – 1:15 | 🛠 Exercise 1: Breaking Things                       |
| 1:15 – 1:30 | ☕ Break                                              |
| 1:30 – 2:15 | 🧠 Theory Block 2: What Actually Breaks in Production |
| 2:15 – 3:45 | 🛠 Exercise 2: Fixing Production Issues              |
| 3:45 – 4:00 | 🎯 The 2 Layers of API Security                      |

<!--
Walk through the agenda briefly. Emphasise: the exercises are the core — theory gives you vocabulary for what you're about to do hands-on.
Practical note: make sure everyone has Docker running and the API pulled. Ask them to run `docker compose up` now if they haven't yet.
-->

---

<!-- ═══════════════════════════════════════════════════
     BLOCK 1 — THEORY
══════════════════════════════════════════════════════ -->

# 🧠 Block 1
## Why Most APIs Are Fake Secure

<!--
Transition slide. Pause here. Let people settle.
-->

---

## The core insight

> Security isn't a feature you add at the end —
> it's a foundation you build from day one.

Most applications still get it wrong.

**If auth, validation, and encryption are weak → nothing else matters.**

Three fundamentals. If any one is broken, your API is broken.

<!--
This is the thesis of the whole course. Pause after reading the quote out loud.
Ask: "Who here has shipped something and called it 'secure' because it had a login page?"
The point: most teams implement auth and stop there — they never check if auth is actually enforced on every resource.
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
The classic mistake: teams implement authentication (JWT check passes) and forget authorization (does this resource belong to YOU?).
Authentication = bouncer checks your ID. Authorization = bouncer checks if you're on the VIP list.
Emphasise: the payload is base64 — anyone can decode it. Never put internal IDs, roles, or sensitive data in there.
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
Walk through the diagram slowly. Key question: "What happens if a token is stolen?"
With 24h expiry and no refresh token: attacker has a full day to do whatever they want.
With 15min access token + refresh rotation: stolen token expires in 15 min, and rotating refresh tokens mean a stolen refresh token is detected on next use.
The vulnerable API's secret is 9 characters — any offline dictionary attack breaks it in seconds.
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
IDOR is OWASP API Security Top 10 #1. It's the most common real-world API vulnerability.
Show both code blocks together — the controller blindly passes id, the service blindly trusts it.
Ask: "How many of you have a `findById` somewhere without an ownership check after it?"
This is where people start getting uncomfortable — that's good. That's the point.
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
Key insight: the check can't live in the controller because the service can be called from other places too — scheduled jobs, event listeners, internal service calls. If the check is only in the controller, those callers bypass it entirely.
The service layer is the only place that's always executed regardless of how it's called.
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
Repeat "Never trust input" out loud — twice. Ask the room to say it with you once. Sounds silly but it sticks.
Real example to share: a company had a path variable that wasn't sanitised, used in a file path, and an attacker used `../../etc/passwd` to read system files. Simple path traversal, completely preventable.
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
Walk through the contrast. @NotBlank only checks that the string isn't empty — it says nothing about what's in it.
The @Pattern annotation rejects anything that isn't alphanumeric/punctuation — stops injection characters.
@DecimalMax prevents someone sending amount=99999999 to overflow balances.
Mention: custom validators are for business rules — "an account can't transfer more than its balance", "a username can't already exist". Bean validation can't express those, you need a custom ConstraintValidator.
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
This is embarrassing when it happens in production — and it happens more than you'd think.
Common cause: a developer adds a field to debug something, the PR reviewer misses it, it ships.
The fix pattern: always use a dedicated response DTO. Never return your JPA entity directly from a controller.
The vulnerable API returns the plain-text password in GET /api/users/me — participants will see this in Exercise 1.
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
"Table stakes" is intentional — this isn't advanced, it's the minimum.
BCrypt cost factor 12: takes ~300ms to hash on modern hardware. Slow enough to defeat brute force (300ms × 1 billion guesses = 9.5 years). Fast enough that a real login feels instant.
For secrets: environment variables are the minimum. Vault (HashiCorp), AWS Secrets Manager, or GCP Secret Manager for production.
The vulnerable API stores passwords as plain text — participants will see them returned in responses during Exercise 1.
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
Quick check-in: "For each of these — hands up if your current project does this." Look for what's missing.
This sets up the exercise well: "You're about to break an API that's missing all six."
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
Before releasing the room: do the first request live on the projector.
1. Run "Login as Alice" → show the token auto-saving in Postman
2. Run "🔥 IDOR – Alice reads Bob's account (id=2)" → show Bob's balance appearing
Then say: "Your turn. Work through all 5 challenges."
Walk the room. Most people finish challenges 1–3 quickly. The brute-force and webhook ones are the eye-openers.
-->

---

# ☕ Break — 15 minutes

> "Everything you just broke is because one of
>  those 3 fundamentals was missing."

See you back here in 15.

<!--
Hard stop. Don't let it run over — people need to decompress after the exercise.
Use this quote as your closing line before people stand up. It reframes what they just did.
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
Walk through the table row by row — keep it fast, one sentence per row.
The goal is pattern recognition: almost every vulnerability has the same root cause (missing check, missing validation, missing verification).
Land on: "None of these required a sophisticated attack. Just knowing where to look."
-->

---

<!-- ═══════════════════════════════════════════════════
     BLOCK 2 — THEORY
══════════════════════════════════════════════════════ -->

# 🧠 Block 2
## What Actually Breaks in Production

<!--
Energy is usually lower after break. Start with a strong opening line rather than easing in.
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
Pause on the bullet list. Ask: "Has anyone here dealt with a scraper? A brute-force bot?"
Let someone share briefly — makes the next slides personal and not hypothetical.
The key shift: Block 1 was about protecting your logic. Block 2 is about surviving real traffic.
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
If you have the API running locally: run the curl command live. No headers. Then say "this is what Exercise 2 will fix."
HSTS: tells browsers to only use HTTPS — prevents SSL stripping attacks.
X-Frame-Options: DENY prevents your app being embedded in an iframe on a malicious site (clickjacking).
X-Content-Type-Options: nosniff stops browsers from guessing content types (MIME sniffing attacks).
CSP: tells browsers which scripts/resources are allowed to run — stops injected scripts from untrusted CDNs.
This is literally 5 lines of Spring Security config. The cost/benefit is enormous.
-->

---

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
The 308 million calculation always lands well. Do it with the room: "26 to the power of 6 — who knows the answer?"
At 100 req/s that's 35 minutes. Dictionary of 10,000 common passwords at 100 req/s = 100 seconds.
Endpoint-specific is the key word — a global rate limit of 1000 req/min doesn't help if 999 of those can all be login attempts.
Ask: "Does anyone know if their login endpoint is rate limited right now?" Usually silence.
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
The sneaky part: Postman ignores CORS entirely — it's a browser security mechanism. So you won't catch this in testing.
It only manifests when a browser from evil-site.com makes a credentialed request to your API. The browser sends the user's cookies, your API accepts it, and the attacker's site reads the response.
Real scenario: a user is logged into your app, visits evil-site.com, that page makes a fetch() to your API — with wildcard CORS and credentials enabled, it succeeds.
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
The timing attack explanation is worth slowing down for. String.equals() short-circuits — it returns false the moment it finds a non-matching character. So comparing "sha256=aaaa" vs "sha256=baaa" returns faster than "sha256=aaaa" vs "sha256=aaab". An attacker can measure microsecond differences over thousands of requests and deduce the correct signature character by character.
MessageDigest.isEqual() always compares ALL bytes regardless of where the mismatch is — constant time.
GitHub, Stripe, and every major webhook provider use exactly this pattern.
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
Same check-in as Block 1: "For each of these — does your production API have it?"
Set up the exercise: "You just broke this API. Now you're the engineer who got paged at 2am because of it. Fix it."
-->

---

<!-- ═══════════════════════════════════════════════════
     EXERCISE 2
══════════════════════════════════════════════════════ -->

# 🛠 Exercise 2
## Fixing Production Issues

**⏱ 90 minutes** — You're now the engineer after the incident.
Open `docs/exercise-2-fixing-production.md`

<!--
Remind participants: every fix requires a rebuild — `docker compose up --build`.
Fix 1 (IDOR ownership check) takes longest — if someone is stuck after 10 min, walk through it together.
Fix 5 (HMAC webhook) has the most "aha" moment around constant-time comparison — worth pausing for discussion.
Pair slower participants with faster ones for the last 20 min.
-->

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

<!--
Same format as the Exercise 1 debrief — walk the table quickly.
Then pause on the last line. Let it land before moving to closing.
-->

---

<!-- ═══════════════════════════════════════════════════
     CLOSING
══════════════════════════════════════════════════════ -->

# 🎯 The 2 Layers of API Security

---

## Layer 1 — Don't get hacked instantly

These are non-negotiable. If any one is missing, the API is broken.

| | |
|--|--|
| 🔐 | **Auth** — short-lived JWT, refresh token rotation |
| ✅ | **Validation** — type, range, pattern, ownership |
| 🔒 | **Encryption** — bcrypt, HTTPS, vault for secrets |

---

## Layer 2 — Survive real traffic

These protect you when your code is correct but the world isn't.

| | |
|--|--|
| 🛡️ | **Rate Limiting** — per-endpoint, per-IP |
| 🪟 | **Security Headers** — stop attacks before your code runs |
| 🌐 | **CORS** — explicit allowlist, never wildcard with credentials |
| 🔗 | **Webhook HMAC** — constant-time verification |

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
Read this slowly. Pause after "at scale."
The mental model shift: most people imagine sophisticated hackers. Reality is scripts probing millions of APIs looking for these exact gaps. Automated, opportunistic, at scale.
This is why fixing the basics matters more than advanced techniques.
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
Ask 2–3 people to share which one they'll check first. Creates commitment, ends on energy.
The `findById` search is particularly powerful — people can literally Ctrl+F their codebase right now on their laptop.
-->

---

## Thank you 🙏

**Articles that inspired this course:**
- [Security Practices That Actually Protect Production Applications (Part 1)](https://medium.com/@madzia912/security-practices-that-actually-protect-production-applications-part-1-ebbe25f031d3)
- [What Actually Breaks in Production (Part 2)](https://medium.com/@madzia912/security-practices-that-actually-protect-production-apps-after-theyve-been-attacked-ce9a13363da3)

**Source code:** `https://github.com/codeclubcph/api-security-techniques`

> Questions?

<!--
Common questions to prepare for:
- "How do I convince my team to prioritise this?" → Frame it as risk, not best practice. IDOR is OWASP #1 for a reason.
- "What about OAuth2 / Keycloak?" → Great for delegated auth. The ownership-check problem still exists behind it.
- "Should we use an API Gateway for rate limiting?" → Yes, AND at the app level. Defense in depth.
- "What about GraphQL / gRPC?" → Same principles apply. IDOR doesn't care about your protocol.
Leave at least 10 minutes for questions. If the room is quiet, ask: "What was the most surprising thing you broke today?"
-->
