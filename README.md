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

## Student page

Students open `http://<instructor-laptop-ip>:8080/student.html` on their phones, enter the session
number and their roll number, and answer as questions are released. Nothing to install; no internet
needed (all scripts are served by the app itself).

## API

Instructor (REST):

| Action | Endpoint |
|---|---|
| Create session | `POST /api/sessions` `{"title": "..."}` |
| Upload roster (CSV, one roll number per line, optional header/name column) | `POST /api/roster/{sessionId}/upload` (multipart `file`) |
| Add question | `POST /api/sessions/{sessionId}/questions` `{"orderIndex":1,"type":"MCQ","text":"...","options":[...],"correctOptionIndex":2,"durationSeconds":60}` |
| Release next question | `POST /api/sessions/{sessionId}/questions/{questionId}/release` |
| Extend a released question | `POST /api/sessions/{sessionId}/questions/{questionId}/extend?seconds=N` |

Student:

| Action | Endpoint |
|---|---|
| Join (roster-verified) | `POST /api/roster/{sessionId}/join` `{"rollNo":"...","deviceToken":"..."}` |
| Current state (own option order, server-computed time left, already answered?) | `GET /api/sessions/{sessionId}/state?rollNo=...&deviceToken=...` |
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
- **Week 3 (Ajay), not yet built:** attendance derivation, first instructor dashboard screen.
