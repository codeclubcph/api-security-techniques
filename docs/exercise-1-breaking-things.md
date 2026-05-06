# 🛠 Exercise 1 — Breaking Things
### ⏱ 60 minutes | Difficulty: Guided

> **Goal:** Find and exploit 5 real vulnerabilities in the Wallet API.
> By the end of this exercise you will have accessed another user's data,
> seen their plain-text password, and understood exactly why it happened.

---

## Setup checklist

- [ ] API is running: `docker compose up` in the `api/` folder
- [ ] Postman is open with the **"API Security Techniques – Wallet API"** collection imported
- [ ] You've logged in as Alice (run **"1 – Authentication → Login as Alice"** — token auto-saved)

---

## The scenario

You are **Alice** (`alice` / `password123`).
The API has three other users: **Bob** (`bob`), **Charlie**, and **admin**.

Your job: prove you can access their data using only Alice's token.

---

## Challenge 1 — IDOR on User Profiles (10 min)

### Step 1 — Fetch your own profile
Run: **"2 – Exercise 1: Breaking Things → My Profile (Alice – OK)"**

Look at the response. Notice anything sensitive?

```json
{
  "id": 1,
  "username": "alice",
  "email": "alice@example.com",
  "password": "password123",   // ← What is this doing here?
  "role": "USER"
}
```

**Vulnerability:** Sensitive data leakage — the password field is returned in the API response.

---

### Step 2 — Read Bob's profile
Run: **"🔥 IDOR – Alice reads Bob's profile (id=2)"**

You are Alice. You just read Bob's full profile — including his password `hunter2`.

**Vulnerability:** `GET /api/users/{id}` has no ownership check. Any authenticated user
can read any other user's profile by incrementing the ID.

### Step 3 — Read the Admin's profile
Run: **"🔥 IDOR – Alice reads Admin profile (id=4)"**

You now have the admin's password. In a real system, what could you do with that?

**Discussion:** Why is the controller not the right place to stop this?

---

## Challenge 2 — IDOR on Accounts (10 min)

### Step 1 — Fetch your own accounts (correct)
Run: **"My Accounts (Alice – OK)"**
You see Alice's account: `ACC-ALICE-001`, balance `5000.00`. ✅

### Step 2 — Enumerate other accounts
Run: **"🔥 IDOR – Alice reads Bob's account (id=2)"**
Run: **"🔥 IDOR – Alice reads Admin account (id=4)"**

You can see every account's balance by changing the ID.

**Vulnerability:** `GET /api/accounts/{id}` in the service layer performs no ownership check.
The controller passes the raw `{id}` path variable directly through.

**Look at the code:**
```java
// AccountService.java — the bug is here, not in the controller
public Account getAccountById(Long id) {
    return accountRepository.findById(id)          // no "does this belong to the caller?" check
        .orElseThrow(() -> new RuntimeException("Account not found"));
}
```

---

## Challenge 3 — IDOR on Transactions (10 min)

### Step 1 — Read your own transactions
Run: **"Alice's Transactions (account 1 – OK)"**

### Step 2 — Read other users' transactions
Run: **"🔥 IDOR – Alice reads Bob's transactions (account 2)"**
Run: **"🔥 IDOR – Alice reads Admin transactions (account 4)"**

Same pattern — the `accountId` path variable is trusted without verification.

---

## Challenge 4 — Brute-force Login (10 min)

### Step 1 — Try wrong passwords
Run **"🔥 Brute-force Login (wrong passwords)"** 10 times in a row — rapidly.

Notice: the API returns `500` every time. No lockout. No delay. No alert.

### Step 2 — Calculate the risk
A 6-character lowercase password = 26^6 = ~308 million combinations.
At 10 requests/second (easily achievable with Postman Runner or curl):
- 308,000,000 / 10 = **30,800,000 seconds = ~1 year** at 10 req/s
- At 1,000 req/s (a cheap VPS): **3.5 days**
- With a dictionary of 10,000 common passwords: **10 seconds**

**Vulnerability:** No rate limiting on `POST /api/auth/login`.

---

## Challenge 5 — Fake Webhook (10 min)

### Step 1 — Send a forged payment event as Admin (id=4)
Run: **"3 – Exercise 2 → 🔥 Fake Webhook (no signature)"**

The API responds `200 OK` and logs:
```
Received webhook event: payment.completed for account 4
```

**Vulnerability:** The `X-Signature-256` header is received but never validated.
Any external system can trigger internal payment events.

---

## 🎯 Debrief — What did we break?

| Vulnerability | Root cause |
|---------------|-----------|
| IDOR – User profiles | No ownership check in service layer |
| IDOR – Accounts | No ownership check in service layer |
| IDOR – Transactions | No ownership check in service layer |
| Sensitive data leak | Password field in response DTO |
| Brute-force login | No rate limiting on auth endpoint |
| Fake webhook | No HMAC signature verification |

> "Everything you just broke is because one of the 3 fundamentals was missing:
>  auth/authorization, input validation, or encryption."

**Head to the break — Exercise 2 is where you fix all of this.**
