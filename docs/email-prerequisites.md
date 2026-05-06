**Subject:** API Security Techniques Workshop – Please set up your machine before Thursday 🔐

---

Hi everybody,

I'm looking forward to seeing you at the **API Security Techniques** workshop on 7th May 2026 from 17:00-21:00.

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

### 3. A code editor (required)
You'll read and edit Java source files during Exercise 2 — pick whichever you're comfortable with.

- **IntelliJ IDEA Community (recommended, free):** https://www.jetbrains.com/idea/download
- **VS Code (also works fine):** https://code.visualstudio.com

### 4. Git (required)
Needed to clone the course repository.

- Download: https://git-scm.com/downloads
- Verify it's working:
  ```bash
  git --version
  ```

### 5. Pull the API image ahead of time (saves time during the session)
```bash
# Clone the course repo
git clone https://github.com/codeclubcph/api-security-techniques.git
cd api-security-techniques

# Build & start the API
docker compose up --build

# You should see:
# Started VulnerableWalletApplication in X seconds
# Visit http://localhost:8080/actuator/health → {"status":"UP"}

# Stop it when done:
docker compose down
```

### 6. Import the Postman collection
1. Open Postman
2. Click **Import** (top left)
3. Drag and drop the file: `postman/api-security-course.postman_collection.json`
4. The collection **"API Security Techniques – Wallet API"** will appear in your sidebar

### 7. Quick smoke test
With the API running (`docker compose up`):
1. In Postman, open **"1 – Authentication" → "Login as Alice"**
2. Hit **Send**
3. You should get a `200 OK` with a `token` in the response body

If that works — you're ready! ✅

---

## Useful Docker commands (for reference during the workshop)

```bash
docker compose up --build   # Build and start the app
docker compose up           # Start without rebuilding
docker compose down         # Stop and remove containers
docker compose logs -f      # Follow live logs
docker compose restart      # Restart after code changes
```

---

## Trouble with setup?

The most common issue is Docker Desktop not being started — make sure the whale icon is visible in your taskbar/menu bar before running `docker compose up`.

---

## What we'll cover

The workshop is structured as two theory blocks and two hands-on exercises:

| Time        | Block                                      |
|-------------|--------------------------------------------|
| 0:00 – 0:15 | Introduction                               |
| 0:15 – 0:45 | Theory: Why most APIs are fake secure      |
| 0:45 – 1:15 | **Exercise 1**: Breaking a vulnerable API  |
| 1:15 – 1:30 | ☕ Break                                    |
| 1:30 – 2:15 | Theory: What actually breaks in production |
| 2:15 – 3:45 | **Exercise 2**: Fixing production issues   |
| 3:45 – 4:00 | Closing: The 2 layers of API security      |

---

## Prior knowledge expected

- You have written or consumed REST APIs before
- Basic familiarity with HTTP (methods, status codes, headers)
- You don't need to know Java — all code snippets are explained

---

See you Thursday!

Magdalena
