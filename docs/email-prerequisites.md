**Subject:** API Security Techniques Workshop – Please set up your machine before Thursday 🔐

---

Hi everybody,

I'm looking forward to seeing you at the **API Security Techniques** workshop on 7.05.2026 from 5pm.

Before the session starts, please spend a moment on getting everything installed. The exercises depend on a running API and Postman — if you arrive without them set up, you might miss the first exercise.

---

## What you'll need

### 1. Docker Desktop (required)
The API runs inside Docker so it works identically on Mac, Windows, and Linux.

- Download: https://www.docker.com/products/docker-desktop/
- After installing, open a terminal and verify:
  ```bash
  docker --version         # Docker version 24.x or higher
  docker compose version   # Docker Compose version v2.x or higher
  ```

### 2. Postman (required)
We'll use Postman to interact with the API during exercises.

- Download: https://www.postman.com/downloads/
- Create a free account if you don't have one (needed to import collections)

### 3. Pull the API image ahead of time (saves time during the session)
```bash
# Clone the course repo
git clone https://github.com/your-org/api-security-techniques.git
cd api-security-techniques

# Build & start the API
docker compose up --build

# You should see:
# Started VulnerableWalletApplication in X seconds
# Visit http://localhost:8080/actuator/health → {"status":"UP"}

# Stop it when done:
docker compose down
```

### 4. Import the Postman collection
1. Open Postman
2. Click **Import** (top left)
3. Drag and drop the file: `postman/api-security-course.postman_collection.json`
4. The collection **"API Security Techniques – Wallet API"** will appear in your sidebar

### 5. Quick smoke test
With the API running (`docker compose up`):
1. In Postman, open **"1 – Authentication" → "Login as Alice"**
2. Hit **Send**
3. You should get a `200 OK` with a `token` in the response body

If that works — you're ready! ✅

---

## What we'll cover

The workshop is structured as two theory blocks and two hands-on exercises:

| Time | Block |
|------|-------|
| 0:00 – 0:45 | Theory: Why most APIs are fake secure |
| 0:45 – 1:45 | **Exercise 1**: Breaking a vulnerable API |
| 1:45 – 2:00 | ☕ Break |
| 2:00 – 2:45 | Theory: What actually breaks in production |
| 2:45 – 3:45 | **Exercise 2**: Fixing production issues |
| 3:45 – 4:00 | Closing: The 2 layers of API security |

---

## Prior knowledge expected

- You have written or consumed REST APIs before
- Basic familiarity with HTTP (methods, status codes, headers)
- You don't need to know Java — all code snippets are explained

---

## Nice to have (but not required)

- A Java IDE (IntelliJ IDEA / VS Code with Java extension) — if you'd like to edit the API source during Exercise 2
- Basic understanding of JWT tokens

---

## Questions?

Reply to this email or reach me at magdalena@tbsi.it.

See you Thursday!

Magdalena
