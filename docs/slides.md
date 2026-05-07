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
Welcome everyone. Two quick show of hands to start:
1. "Who has written or maintained a REST API?" — almost everyone raises their hand.
2. "Who has had a security incident — or suspects they might have and just didn't know about it?" — a few hands, some nervous laughs.

Use that gap. Most people in the room have probably shipped a vulnerability. Today they'll know what it looks like and how to stop it.

Housekeeping before starting: Docker running? Repository cloned? Run `docker compose up` now — it takes 30–60 seconds to pull and start.
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
Walk through the agenda in under 60 seconds — don't read it out line by line. Just land the structure: two theory blocks, two exercises, the exercises are the point.

Key message: "The theory gives you vocabulary for what you're about to do. You'll understand everything in the exercise because you just heard it."

Remind about Docker now if you haven't already. Anyone who hasn't pulled the repo needs to do it during the first theory block.
-->

---

<!-- ═══════════════════════════════════════════════════
     BLOCK 1 — THEORY
══════════════════════════════════════════════════════ -->

# 🧠 Block 1
## Why Most APIs Are Fake Secure

<!--
Transition slide. Pause briefly. Let people settle after the agenda overview.
One line to open: "Everything we cover in the next 30 minutes, you'll immediately prove in the exercise." That creates anticipation rather than passive listening.
-->

---

## The core insight

> Security isn't a feature you add at the end —
> it's a foundation you build from day one.

Most applications still get it wrong.

**If auth, validation, and encryption are weak → nothing else matters.**

Three fundamentals. If any one is broken, your API is broken.

<!--
This is the thesis of the whole course. Read the quote out loud and pause after it — let it sit.

Ask: "Who here has shipped something and called it 'secure' because it had a login page?" A few hands usually go up; some people laugh recognising themselves.

The point: having a login page and enforcing authentication on every resource are different things. Most teams do the former and assume it implies the latter. Today they'll see exactly why it doesn't.

Keep this slide to about 1 minute. The real content is in the next slides.
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
Spend a moment on the authentication vs authorization distinction — many developers conflate them. Use the analogy: authentication is the bouncer checking your ID at the door; authorization is checking if you're on the VIP list once you're inside.

The common failure: teams implement the JWT check (authentication) and assume it means users can only access their own data. It doesn't. The JWT only proves who you are — it says nothing about what you're allowed to see.

On the JWT payload point: demonstrate this live if you can. Take Alice's token from Postman, go to jwt.io, paste it — the payload decodes instantly without knowing the secret. "Never put anything in here you wouldn't put on a billboard."

Transition: "Let's look at exactly how this plays out in code."
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
Walk through the diagram slowly — trace the arrows. "Client logs in, gets two tokens. Uses the short-lived one for requests. When it expires, uses the refresh token to get a new access token."

Key question to ask the room: "What happens if a token is stolen?" Let someone answer.

With a 24h token and no refresh: the attacker has a full day, and you have no way to invalidate it — it's stateless.
With 15min access + refresh rotation: stolen access token expires in 15 minutes. If the refresh token is stolen and used, the rotation means the original holder's next refresh fails — you detect the theft.

The vulnerable API's secret is `secret123` — 9 characters, in plain text in application.yml. In Exercise 1, participants crack it in seconds at jwt.io. Ask: "How many of you have a JWT secret that's just a word or phrase?"

Transition: "That secret problem is a symptom of a deeper issue — let's look at the authorization mistake underneath it."
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
IDOR is OWASP API Security Top 10 #1 — the single most common real-world API vulnerability. Mention that: it's not a theoretical risk, it's what attackers actually look for first.

Point at both code blocks together. "The controller gets an ID from the URL and passes it to the service. The service calls findById and returns whatever it finds. Where does it check that the caller is allowed to see this account?" Pause. "It doesn't."

Ask: "How many of you have a `findById` call somewhere in your codebase without an ownership check right after it?" Let the hands go up. Let the moment land.

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
Read the code out loud. "We accept a callerUsername, look up who they are, look up the account, compare owners, throw if they don't match."

Key insight to emphasise: why the service layer specifically? Because the controller is not the only caller. Scheduled jobs, async event listeners, internal service-to-service calls — none of them go through the controller. If your check is in the controller, those paths bypass it entirely. The service layer is the only place that's always executed regardless of how it's invoked.

This is also why testing the service in isolation is so valuable — the test proves the check is enforced without needing an HTTP request.

Transition: "Let's look at the other two fundamentals quickly, then you'll break all of this yourself."
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
Repeat "Never trust input" out loud twice, then ask the room to say it with you once. Sounds corny — it sticks anyway.

The list on the slide is deliberate. Most developers think of request bodies. They forget path variables (IDOR lives here), query parameters (injection lives here), headers (forgeable), and webhook payloads (that's what Exercise 1 Challenge 5 is about).

Real example to share: path traversal — a path variable used to build a file path, unsanitised. An attacker sends `../../etc/passwd` as the ID. The server reads the system file and returns it. Simple, completely preventable, real.

Another one: a `keyword` search parameter passed directly into a SQL LIKE clause. Even if parameterised, a wildcard-heavy query like `%a%a%a%a%` forces a full table scan on every character — ReDoS via the database. We have exactly this in the vulnerable API's search endpoint.
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
Walk through the contrast. @NotBlank only checks that the string isn't empty — says nothing about what's in it.

@Pattern rejects anything that isn't alphanumeric or standard punctuation. That single annotation blocks SQL injection characters, script tags, and path traversal attempts before they even reach your service.

@DecimalMax — ask: "What happens if someone sends amount=999999999?" If there's no cap, a malicious transfer request could overflow balance fields. One annotation prevents it.

The custom validator point: Bean Validation is for structural rules (format, length, range). Business rules — "you can't transfer more than your balance", "this username is already taken" — need a custom ConstraintValidator or explicit checks in the service layer.

Keep this to 2 minutes — it's not the main event, it's vocabulary for the exercise.
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
Read the JSON out loud and pause on `"password": "password123"`. Let the room react.

This is embarrassing when it happens in production — and it happens more than you'd think. Common cause: a developer adds a field to debug, the PR reviewer misses it, it ships. It stays there for months. Then someone curls the endpoint and writes it up on Twitter.

The pattern that prevents this: never return your JPA entity directly from a controller. Always map to a dedicated response DTO. The DTO is an explicit allowlist of what you're willing to share. If a field isn't in the DTO, it can't leak.

In our API, `UserResponse` had `password` in it — participants will see this in Exercise 1, and they'll delete that one field in Fix 2.

Real world: the Peloton API (2021) returned private user data — age, gender, city, workout history — to any authenticated user, no matter whose account it was. Classic DTO + IDOR combination.
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
"Table stakes" is intentional — call it out. This isn't Block 2 advanced stuff, it's the minimum bar for any production system.

BCrypt cost factor 12: takes ~300ms to hash on modern hardware. Ask: "Doesn't that make login slow?" No — 300ms is imperceptible to a human. But for an attacker trying a billion passwords offline, 300ms × 1 billion = 9.5 years. The slowness is the whole point.

MD5 and SHA-1 are instant — a modern GPU can test billions per second. A leaked MD5 password database is cracked in hours. BCrypt-12 takes years on the same hardware.

Secrets in config: `.env` files or environment variables are the minimum for any team. For production, use HashiCorp Vault, AWS Secrets Manager, or GCP Secret Manager. "Never commit a secret to git — not even temporarily, not even in a private repo."

The vulnerable API has `secret: secret123` in `application.yml` — committed to git, hardcoded, 9 characters. Participants crack it in Exercise 1 Challenge 7 in about 10 seconds.
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
Do the check-in: "For each line — hands up if your current production API does this." Go through slowly. Watch for the lines where hands drop.

Common gaps in the room: short-lived JWTs (most APIs use 24h), ownership checks (almost nobody checks every findById), response DTOs (lots of entity-returning controllers out there).

Close the slide with: "The API you're about to break is missing all six of these. Your job is to find exactly where each one is missing."

That's the bridge into the exercise. Move quickly to the break — energy is high, don't let it drop.
-->

---

# ☕ Break — 5 minutes

> "You now have the vocabulary. Time to see it in the wild."

See you back in 5.

<!--
Short break — keep it tight. People are usually energised going into the first exercise.
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
Before releasing the room — do two requests live on the projector, it takes 60 seconds and sets the tone:
1. Run "Login as Alice" → show the token auto-saving in the collection variables panel.
2. Run "🔥 IDOR – Alice reads Bob's account (id=2)" → show Bob's balance and full profile appearing. Pause. "Alice is logged in. She changed one digit in the URL. That's it."

Then say: "Your turn. Open exercise-1-breaking-things.md and work through the challenges in order."

Facilitation notes:
- Challenges 1–3 (IDOR) go quickly — most people finish in 10 minutes. That's fine, those are the ones that land the main point.
- Challenge 4 (brute force) is slower because they need to hit the endpoint multiple times manually. Point them toward the Postman Runner if they want to automate it.
- Challenge 5 (fake webhook) is usually the most surprising — "anyone can trigger a payment event?"
- Challenges 6 (actuator) and 7 (JWT at jwt.io) are marked bonus. Point faster participants there.
- Walk the room. Ask "what did you find?" rather than giving answers.
-->

---

# ☕ Break — 5 minutes

> "Everything you just broke is because one of
>  those 3 fundamentals was missing."

See you back in 5.

<!--
Hard stop. Use this quote as your closing line before people stand up — it reframes what they just did.
Short break intentional: energy is high after the exercise, carry it into the debrief.
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

After the table, pause and say: "Look at the root cause column. Missing check. Missing check. Missing check. No limiting. No verification. No auth. No auth. Weak secret. No auth."

Land on: "None of these required a sophisticated attack. No zero-days. No exploit frameworks. Just knowing where to look and trying the obvious thing."

That's the shift in mindset this course is trying to create. Let it sink in before moving to Block 2.
-->

---

# ☕ Break — 20 minutes

> "You've broken it. After the break — you fix it."

<!--
Longest break of the day — intentional. People need to recharge before the 85-minute exercise.
Use this time to reset the room: check that everyone has the repo open and can run the tests.
-->

---

<!-- ═══════════════════════════════════════════════════
     BLOCK 2 — THEORY
══════════════════════════════════════════════════════ -->

# 🧠 Block 2
## What Actually Breaks in Production

<!--
Energy is usually lower coming back from the 20-minute break. Don't ease in — open with a strong line before you even click to the next slide.

Try: "You just broke nine things in a running API. All of them without admin access. All of them in under 30 minutes. Block 2 is about making sure that never happens in your system."

That reframes the break: it wasn't downtime, it was the moment between breaking and fixing.
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
Pause on the bullet list. Ask: "Has anyone here dealt with a scraper? A bot hammering a login endpoint? A partner that sent a malformed webhook payload that triggered something it shouldn't have?"

Let someone share briefly — it makes the next slides feel like solutions to real problems rather than theoretical best practices.

The framing shift to make explicit: Block 1 was about protecting your business logic (auth, validation, encryption). Block 2 is about surviving the internet — adversarial traffic that doesn't care about your logic at all.

"The techniques in this block stop attacks before your code even runs. Not better code — a different layer of defence."
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
Do the 308 million calculation interactively: "26 to the power of 6 — who knows the answer?" Let someone shout it out. Then work through the time: at 100 req/s, that's 35 minutes for exhaustive search. A dictionary of the 10,000 most common passwords at 100 req/s = 100 seconds.

"How hard is 100 req/s? You can do that with a curl loop on a laptop. A cheap VPS gets you 10,000 req/s."

Endpoint-specific is the critical detail. A global rate limit of 1000 req/min doesn't help if all 1000 can be login attempts from the same IP. The login endpoint needs its own tight limit.

Ask: "Does anyone know right now, without checking, whether their login endpoint is rate limited?" — usually silence, maybe one hand. "That's the answer to 'are we at risk.'"

We use Bucket4j in the exercise — 5 attempts per minute per IP. That's enough to not annoy legitimate users but makes brute force computationally infeasible.
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
Same check-in as Block 1: "For each of these — hands up if your current production API has it." Go slowly. Watch for the rate limiting and security headers lines — those are usually the two where almost no hands go up.

Close with: "You just broke this API in Exercise 1. Now you're the engineer who got paged at 2am because someone else did the same thing. Exercise 2 is your incident response."

That framing makes the exercise feel urgent and real rather than an academic exercise.
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
Before releasing the room: "After each fix, run `./gradlew test` — you'll see the test go from red to green. That's your verification. If it's green, it works. No need to rebuild Docker just to check."

Facilitation notes:
- Fix 1 (25 min): The IDOR fix is the core. If someone is stuck after 10 minutes, walk through 1a together on the projector, then let them do 1b and 1c independently — the pattern is identical.
- Fix 2 (5 min): Easy win. If someone finishes Fix 1 early, nudge them straight to Fix 2 so they feel momentum.
- Fix 3 (10 min): The most common stumble is missing imports. Remind the room to check the step 1 imports before the code block.
- Fixes 4–8: These are "if time." Fast groups will get through all 8. Don't stress if slower groups only reach Fix 3 — they've fixed the most important vulnerabilities.
- Pair faster and slower participants once you're in the last 20 minutes. Teaching a concept cements it.
- For the HMAC constant-time comparison (Fix 5): if the room gets there, pause and walk through the timing attack explanation — it's the most memorable moment of the day.
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
Walk the table fast — one sentence per row. Pause on BCrypt and rate limiting if the room hasn't done those yet; name them as "the ones to take home and implement on Monday."
Land hard on the last line. Read it out. Pause. Then move to closing — don't rush this moment.
The goal: participants should leave feeling like they did something real today, not attended a lecture.
-->

---

# ☕ Break — 5 minutes

> "Last one. Then we wrap up."

Back in 5.

<!--
Final short break before key takeaways. Lets people decompress after the long exercise.
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
Read each row slowly. After Auth: "Not just 'does a token exist' — does this token belong to someone who's allowed to do this specific thing?"
After Validation: "Every endpoint. Every input. Not just the ones that feel risky."
After Encryption: "Not optional. Not 'we'll add it later.' This is the floor."

"Non-negotiable" is the key word. Frame it as: you can ship without Layer 2 and be okay for a while. You cannot ship without Layer 1 and call it production-ready.
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
The framing here is important: "Your code can be perfect and you can still be exploited — because the threat isn't inside your logic, it's in the traffic pattern."

Rate limiting stops brute force before authentication even runs. Security headers stop entire attack classes at the browser level. CORS stops cross-origin data theft without touching your endpoints. HMAC stops forged events at the network boundary.

All four happen before your business logic executes. That's the point of this layer.

After reading the table: "Both layers together — that's what a production-ready API looks like."
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