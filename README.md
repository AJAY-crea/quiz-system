# quiz-system

CS5013 TEAM-AM — An Instructor-Paced In-Class Quiz and Response System.
See `../design_doc.md` and `../proposal.md` for the architecture, module ownership, and milestone plan.

## Run

```
./mvnw spring-boot:run       # Linux/macOS/Git Bash
mvnw.cmd spring-boot:run     # Windows cmd/PowerShell
```

Starts on http://localhost:8080. The H2 console is at /h2-console (JDBC URL
`jdbc:h2:file:./data/quizdb`, user `sa`, empty password).

## Test

```
./mvnw test
```

## Running a class

1. On the instructor's laptop, open **http://localhost:8080/instructor.html**. This page and all
   instructor actions only work from the laptop itself.
2. Create a session, upload the roster CSV, and add questions.
3. Pick the laptop's classroom Wi-Fi address (laptops list VPN/VirtualBox adapters too), then
   **Open joining** for e.g. 60 s. Students scan the QR with their phone camera. The code changes
   every 15 s, so a forwarded photo stops working; the session number and code under the QR are a
   typed fallback.
4. **Release next question**, and use **+30 s** if needed. Answers arrive live.
5. **Refresh attendance**: present = answered at least 50% of released questions (late answers
   count). Flags such as "device was already used by …" are for review, never penalties.
6. **Download score sheet (.xlsx)**: one row per rostered student in roll-number order, absentees
   included. A CSV copy is there as a fallback.
7. **Doubts** appear live, tagged with the question that was open. They're anonymous unless you
   tick "Show who asked", and you can dismiss them or turn the channel off.
8. **Integrity signals** list how often, and for how many seconds, a student left the page (or the
   screen went off), exited full screen, or pasted while a question was open, answered or not.
   Treat them as prompts to look, not proof: a page can't tell "switched app" from "screen off".

Students need nothing installed. On the same Wi-Fi (or the laptop's hotspot), no internet is needed:
everything is served by the laptop.

### Students on mobile data (optional internet link)
Phones on mobile data can't reach a laptop's local address. A free Cloudflare quick tunnel gives the
laptop a public `https://` link (the laptop needs internet):

1. Install once: `winget install --id Cloudflare.cloudflared`
2. Start the app, then in a second terminal: `cloudflared tunnel --url http://localhost:8080`
   It prints a link like `https://some-words.trycloudflare.com` (new each time).
3. Restart the app with that link so the dashboard offers it first for the QR:
   `java -jar target\quiz-system-0.1.0-SNAPSHOT.jar --quiz.public-url=https://some-words.trycloudflare.com`

Instructor actions stay laptop-only: requests arriving through a tunnel are always treated as phones.
Over https the student page also keeps the screen awake while a question is open.

## API

Instructor (REST, localhost only):

| Action | Endpoint |
|---|---|
| Dashboard data | `GET /api/sessions/{sessionId}/dashboard` |
| Open / close joining | `POST /api/sessions/{sessionId}/join-window/open?seconds=N`, `.../join-window/close` |
| Current join code | `GET /api/sessions/{sessionId}/join-window` |
| Join QR (SVG) | `GET /api/sessions/{sessionId}/join-qr.svg?base=http://<laptop-ip>:8080` |
| Attendance | `GET /api/sessions/{sessionId}/attendance` |
| Score sheet | `GET /api/sessions/{sessionId}/export.xlsx` (fallback: `export.csv`) |
| Doubts list / dismiss / settings | `GET /api/sessions/{sessionId}/doubts`, `POST .../doubts/{doubtId}/dismiss`, `POST .../doubts-settings?enabled=&authorsVisible=` |
| Integrity summary | `GET /api/sessions/{sessionId}/integrity` |
| Create session | `POST /api/sessions` `{"title": "..."}` |
| Upload roster (CSV, one roll number per line, optional header/name column) | `POST /api/roster/{sessionId}/upload` (multipart `file`) |
| Add question | `POST /api/sessions/{sessionId}/questions` `{"orderIndex":1,"type":"MCQ","text":"...","options":[...],"correctOptionIndex":2,"durationSeconds":60}` |
| Release next question | `POST /api/sessions/{sessionId}/questions/{questionId}/release` |
| Extend a released question | `POST /api/sessions/{sessionId}/questions/{questionId}/extend?seconds=N` |

Student:

| Action | Endpoint |
|---|---|
| Exchange scanned QR code for a 2-minute join ticket | `POST /api/sessions/{sessionId}/join-ticket?code=...` |
| Join (roster-verified; ticket not needed to rejoin from the same phone) | `POST /api/roster/{sessionId}/join` `{"rollNo":"...","deviceToken":"...","ticket":"..."}` |
| Current state (own option order, server-computed time left, already answered?) | `GET /api/sessions/{sessionId}/state?rollNo=...&deviceToken=...` |
| Send a doubt | `POST /api/sessions/{sessionId}/doubts` `{"rollNo","deviceToken","text"}` |
| Integrity signal (sent with `navigator.sendBeacon`) | `POST /api/sessions/{sessionId}/integrity` `{"rollNo","deviceToken","questionId","eventType"}` |
| Submit answer (STOMP) | send to `/app/session/{sessionId}/answer`, ack on `/user/queue/answer-ack` |
| Question released / deadline extended (STOMP broadcast) | subscribe `/topic/session/{sessionId}/question` |

STOMP endpoint: `/ws` (SockJS) or `/ws/websocket` (plain WebSocket, used by `student.html`).

## Status

- **Week 1:** scaffold, H2 schema, roster upload + verified join, session-engine skeleton, STOMP wiring.
- **Week 2:** server-held timing (300 ms grace), question bank, per-student option shuffle, MCQ scoring.
- **Week 3 (Mukesh):** resilience. Every answer goes through `ResilienceService.accept()`: stored
  once (database unique constraint on session + question + roll number as the final guard), late
  answers kept but marked late, stale devices rejected. The student page keeps unconfirmed answers in
  localStorage and resends them after reconnecting. After an instructor-app restart, sessions resume
  from the database. The instructor can extend a deadline.
- **Week 3 (Ajay):** attendance derivation, the instructor dashboard, and anti-proxy joining:
  - a joining window the instructor controls;
  - a rotating QR code exchanged for a 2-minute join ticket;
  - a flag when one phone is used for different roll numbers, in this or an earlier session;
  - instructor actions restricted to the laptop itself.
- **Week 4 (Mukesh):** integrity signals (page left, full screen exited, paste), advisory only and
  capped per student per question; basic doubts channel (anonymous by default, tagged to the open
  question, dismiss, on/off).
- **Week 4 (Ajay):** basic score-sheet export (.xlsx plus CSV fallback, protected against
  formula injection); the roster now keeps student names.
- **Not built yet (after the mid-demo):** typed-answer review, doubt upvoting and queue,
  per-question analytics, analytics columns in the export, the load-test harness, and an integrity
  false-positive calibration run.
