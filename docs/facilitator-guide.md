# 🎓 Facilitator Guide — API Security Techniques
### 4-hour workshop | For the trainer's eyes only

---

## Pre-workshop checklist (day before)

- [ ] Send the prerequisites email (`docs/email-prerequisites.md`) at least 3 days before
- [ ] Test `docker compose up --build` from scratch on a clean machine
- [ ] Verify Postman collection: all 🔥 attacks return the expected vulnerable responses
- [ ] Prepare the Marp slides export: `npx @marp-team/marp-cli docs/slides.md --pdf`
- [ ] Have the repo URL or ZIP ready to share in chat if anyone didn't clone it

---

## Timing sheet

| Clock | Elapsed | Block | Notes |
|-------|---------|-------|-------|
| 09:00 | 0:00 | Welcome + agenda | 5 min max — people are still arriving |
| 09:05 | 0:05 | Theory Block 1 | Slides 1–12. Pacing: ~3 min/slide |
| 09:45 | 0:45 | Exercise 1 briefing | Show the first Postman request live before releasing |
| 09:50 | 0:50 | Exercise 1 (hands-on) | Walk the room. Most people get stuck on Fix 1 |
| 10:45 | 1:45 | Exercise 1 debrief | 5 min. Ask: "What surprised you?" |
| 10:50 | 1:50 | Break | Hard stop — don't let theory bleed into it |
| 11:05 | 2:05 | Theory Block 2 | Slides 13–22. Rate limiting slide generates most questions |
| 11:50 | 2:50 | Exercise 2 briefing | Remind: API needs rebuild after each fix |
| 11:55 | 2:55 | Exercise 2 (hands-on) | Webhook HMAC takes longest — pair slower participants |
| 12:50 | 3:50 | Exercise 2 debrief | 5 min. Ask: "Which fix would help your current project?" |
| 12:55 | 3:55 | Closing — 2 Layers | Slides 23–27. End with "What to do on Monday" |
| 13:00 | 4:00 | End | |

---

## Common participant questions — and answers

### "Why not use an API Gateway for rate limiting?"
API Gateways (Kong, AWS API Gateway) are great for rate limiting at the edge.
But the lesson here is: even without a gateway, the application itself
should not be infinitely brute-forceable. Defense in depth.

### "Isn't JWT stateless, so how do we revoke tokens?"
Great question — exactly why refresh tokens and short-lived access tokens matter.
Options: short expiry (15 min), token blocklist in Redis, or opaque tokens.
The vulnerable API uses 24h tokens with no refresh — that's the problem.

### "Our app uses sessions, not JWT — is this relevant?"
Yes. IDOR, rate limiting, headers, CORS, and webhooks are all
session-agnostic. The JWT section shows the pattern to aim for if you're
ever redesigning auth.

### "Can I use Spring Security's built-in rate limiting?"
Spring Security doesn't include rate limiting. Use **Bucket4j** with
a Spring filter, or **Resilience4j RateLimiter** for service-layer limiting.
The Bucket4j dependency is already commented in `build.gradle` for Exercise 2.

### "Is the H2 in-memory DB realistic?"
For the exercises — yes. The vulnerability patterns (IDOR, missing validation)
are identical on PostgreSQL, MySQL, or any relational DB.
The course uses H2 so participants don't need a database server.

---

## Exercise 1 — things to watch for

- **Participants who finish early:** Point them to the search endpoint
  `GET /api/transactions/account/{accountId}/search?keyword=X` — the
  missing ownership check works here too.
- **People confused by the token:** Remind them to run "Login as Alice" first.
  The `{{alice_token}}` variable is auto-populated by the test script.
- **People who ask "but I'd never do this in real code":**
  That's the point — show them the git blame on production IDOR bugs that
  cost companies millions. This happens in real codebases constantly.

---

## Exercise 2 — things to watch for

- **Fix 1 (IDOR):** Most time sink. Walk through the controller → service
  pattern live on the projector if the group is struggling after 10 min.
- **Fix 3 (Headers):** Spring Security 6 uses lambda DSL — the old syntax
  `.and().headers()` no longer works. Make sure participants are on Spring Boot 3.x.
- **Fix 5 (HMAC):** The `MessageDigest.isEqual()` constant-time comparison
  is the key insight. Many participants will use `String.equals()` — explain
  timing attacks with a concrete example.

---

## Backup plan — if Docker doesn't work for someone

```bash
# Run directly with Gradle (requires Java 17+)
cd api
./gradlew bootRun
```

The H2 console is also available at `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:mem:walletdb`, username: `sa`, no password).

---

## Energy management tips

- **After Exercise 1 debrief:** The break comes at a natural energy low.
  Keep it hard at 15 minutes — people come back refreshed.
- **Block 2 theory:** Rate limiting slide is the most energising — ask
  the room to calculate how long their own login endpoint would last.
- **Closing:** "What to do on Monday" is intentionally action-oriented.
  Ask 2–3 people to share one thing they'll check in their own codebase.

---

## Slide rendering (Marp)

```bash
# Install Marp CLI once
npm install -g @marp-team/marp-cli

# Export to PDF (for sharing / printing)
marp docs/slides.md --pdf --output docs/slides.pdf

# Export to HTML (interactive, with speaker notes)
marp docs/slides.md --html --output docs/slides.html

# Live preview while editing
marp docs/slides.md --watch --preview
```
