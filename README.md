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

## Run with Docker

The image is built by `.github/workflows/docker-image.yml` (all tests, build, a start-up check) and saved
as `quiz-system-<commit-sha>.tar.gz`.

```
docker load -i quiz-system-<commit-sha>.tar.gz
docker run -p 8080:8080 quiz-system:<commit-sha>
```

Open the **"Instructor dashboard: http://localhost:8080/instructor.html?key=…"** link that the container
prints at start-up. Inside Docker the browser isn't "this laptop", so the dashboard needs that link.
Students open `http://<this machine's address>:8080/student.html` by scanning the QR on the dashboard.
Add `-v quizdata:/data` to keep the database between runs. Build it yourself with `docker build -t quiz-system .`.

## Running a class

1. On the instructor's laptop, open **http://localhost:8080/instructor.html**. This page and all
   instructor actions only work from the laptop itself.
2. Create a session, upload the roster CSV, and add questions one at a time or as a CSV
   (`type,seconds,marks,question,correct,option1,option2,…`; `correct` counts from 1):
   ```
   MCQ,60,1,"Capital of India?",2,Mumbai,Delhi,Chennai
   TYPED,120,5,"Explain Ohm's law"
   ```
   An unreleased question can be removed.
3. Pick the laptop's classroom Wi-Fi address (laptops list VPN/VirtualBox adapters too), then
   **Open joining** for e.g. 60 s. Students scan the QR with their phone camera. The code changes
   every 15 s, so a forwarded photo stops working; the session number and code under the QR are a
   typed fallback.
4. **Release next question**. Use **+30 s** to give more time, **Close question now** if everyone has
   answered (5 s notice), and **End session** at the end. Answers arrive live. What a student has
   written is kept on their phone through reconnects and reloads, and is sent automatically just
   before time ends if they haven't pressed Submit. The first answer sent is final.
5. **Mark typed answers** (during or after the lecture): identical answers are grouped, so you mark
   each wording once, and you can give one student a different mark. Roll numbers stay hidden unless you
   tick the box. Late answers score 0 and can't be marked.
6. **Results by question**: % correct, how many chose each option, and the discrimination index, with
   warnings such as "check the answer key". **Change correct answer** re-scores everyone. **Compare all
   sessions** shows the semester so far. Don't project this during a question: it shows the answers.
7. **Refresh attendance**: present = answered at least 50% of released questions (late answers
   count). Flags such as "device was already used by …" are for review, never penalties.
8. **Download score sheet (.xlsx)**: one row per rostered student in roll-number order, absentees
   included, plus "Question analysis" and "Typed answers" sheets. Unmarked typed answers show "review"
   and aren't in Total yet. A CSV of the scores is there as a fallback.
9. **Show students their own marks** publishes marks to each student's phone: their own only, with
   no answer key and nothing about a question still open.
10. **Doubts** appear live, tagged with the question that was open, most upvoted first. They're anonymous
    unless you tick "Show who asked". Students see open doubts (never who asked) and can upvote each
    once; you can stop this with "Students see and upvote doubts", dismiss a doubt, or turn the channel off.
11. **Integrity signals** list how often, and for how many seconds, a student left the page (or the
    screen went off), exited full screen, or pasted while a question was open, answered or not.
    Treat them as prompts to look, not proof: a page can't tell "switched app" from "screen off". You can
    stop recording or clear them.

Students need nothing installed. On the same Wi-Fi (or the laptop's hotspot), no internet is needed:
everything is served by the laptop.

### Running a real class (one command)

```
powershell -ExecutionPolicy Bypass -File scripts\run-class.ps1 -Tunnel
```

This starts the app with its own database (`data/class`) and opens a Cloudflare quick tunnel in a second
window, so students on **any** network (mobile data, other Wi-Fi) can join. The tunnel's https link
appears first in "Address in the QR" on the dashboard, or paste it into "Internet link". Without
`-Tunnel`, only phones on the laptop's network can join.

Fixing things during a class:
- **Page or script (HTML/JS):** edit the file; phones get it on their next reload. No restart.
- **Java:** run `.\mvnw.cmd -q -DskipTests package`, press Ctrl+C in the app window and run the script
  again **without** `-Tunnel` (the tunnel keeps running, so the link stays the same). Phones reconnect by
  themselves; answers wait on the phones meanwhile, and nothing in the database is lost.

The QR changes every 15 s by default; "QR changes every" on the dashboard sets 5-30 s. Faster makes a
forwarded photo of the QR die sooner, but a slow phone may need to scan again.

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
| Add question | `POST /api/sessions/{sessionId}/questions` `{"orderIndex":1,"type":"MCQ","text":"...","options":[...],"correctOptionIndex":2,"durationSeconds":60,"maxMarks":1}` |
| Upload questions (CSV) | `POST /api/sessions/{sessionId}/questions/upload` (multipart `file`) |
| Remove an unreleased question | `DELETE /api/sessions/{sessionId}/questions/{questionId}` |
| Release next question | `POST /api/sessions/{sessionId}/questions/{questionId}/release` |
| Extend / close a released question | `POST /api/sessions/{sessionId}/questions/{questionId}/extend?seconds=N`, `.../close` |
| End session | `POST /api/sessions/{sessionId}/end` |
| Typed answers, grouped | `GET /api/sessions/{sessionId}/questions/{questionId}/review` |
| Mark a group / one answer (`null` clears) | `POST .../questions/{questionId}/review/group` `{"key","marks"}`, `POST /api/sessions/{sessionId}/responses/{responseId}/marks` `{"marks"}` |
| Publish marks to students | `POST /api/sessions/{sessionId}/marks-visibility?visible=` |
| Analytics | `GET /api/sessions/{sessionId}/analytics`, `GET /api/analytics/semester` |
| Fix an answer key and re-score | `POST /api/sessions/{sessionId}/questions/{questionId}/answer-key?correctOptionIndex=N` |
| Share doubts with students | `POST /api/sessions/{sessionId}/doubts-settings?shared=` |
| Integrity on/off, clear | `POST /api/sessions/{sessionId}/integrity-settings?enabled=`, `DELETE /api/sessions/{sessionId}/integrity` |

Student:

| Action | Endpoint |
|---|---|
| Exchange scanned QR code for a 2-minute join ticket | `POST /api/sessions/{sessionId}/join-ticket?code=...` |
| Join (roster-verified; ticket not needed to rejoin from the same phone) | `POST /api/roster/{sessionId}/join` `{"rollNo":"...","deviceToken":"...","ticket":"..."}` |
| Current state (own option order, server-computed time left, already answered?) | `GET /api/sessions/{sessionId}/state?rollNo=...&deviceToken=...` |
| Own marks (once published) | `GET /api/sessions/{sessionId}/my-marks?rollNo=...&deviceToken=...` |
| Send a doubt | `POST /api/sessions/{sessionId}/doubts` `{"rollNo","deviceToken","text"}` |
| Class's open doubts, without authors | `GET /api/sessions/{sessionId}/doubts/queue?rollNo=...&deviceToken=...` |
| Upvote a doubt | `POST /api/sessions/{sessionId}/doubts/{doubtId}/upvote` `{"rollNo","deviceToken"}` |
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
- **Final milestone (Ajay):** typed-answer marking, per-question and semester analytics, answer-key
  fix with re-scoring, full analytics export, question CSV upload, students' own marks.
- **Final milestone (Mukesh):** doubt upvoting and queue, answer drafts and auto-submit, close question
  and end session, integrity on/off and clear, load-test harness.
- **Not done yet (needs real people):** the integrity false-positive calibration run, and the
  stakeholder dry-run with real phones.

## Load test

Dev-only, not on the dashboard. It writes fake students, so it refuses any database that isn't a throwaway:

```
java -jar target\quiz-system-0.1.0-SNAPSHOT.jar --quiz.loadtest.clients=300 --server.port=8091 --spring.datasource.url=jdbc:h2:mem:load
```

Everything runs on one laptop, so this tests the server and database, not the classroom Wi-Fi.

## Where this differs from the design doc

- Doubts and integrity signals use REST rather than STOMP: they need no push, and a plain request
  survives the page being hidden.
- Question upload is `/api/sessions/{id}/questions/upload`, because a question belongs to a session.
- Added beyond the design doc: marks per question, students' own marks, answer-key fix, close
  question, end session, the switch that stops sharing doubts with students.

## Contact

- Veliginti Ajay Kumar Reddy (CS23B065), cs23b065@smail.iitm.ac.in: roster and joining, question bank,
  scoring and marking, attendance, analytics, export, dashboard, instructor link.
- Babbadi Mukesh Chandu (EE23B093), ee23b093@smail.iitm.ac.in: session engine, timing, resilience,
  integrity signals, doubts, load test.

Weekly contribution log: `CONTRIBUTIONS.md`. Prompts used and AI mistakes found: `PROMPT_LOG.md`.
