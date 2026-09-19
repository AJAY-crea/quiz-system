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

## Week 1 status (Sep 12–18 slice, built Sep 19)

- Maven/Spring Boot scaffold, H2 schema (Student, QuizSession, Question, Response, Doubt,
  IntegrityEvent, plus Participant for roster device-token binding).
- Roster: upload (multipart CSV, `POST /api/roster/{sessionId}/upload`) + verified join
  (`POST /api/roster/{sessionId}/join`), rejecting roll numbers outside the roster and flagging
  (not blocking) a roll number/device-token mismatch. Tests: `RosterServiceTest`,
  `RosterControllerEndToEndTest` (uploads `src/test/resources/fixtures/test-roster.csv` over HTTP).
- Session engine: `POST /api/sessions` to create, `POST /api/sessions/{id}/questions/{qId}/release`
  to release the next question in order (refuses out-of-order release), broadcasting over STOMP to
  `/topic/session/{id}/question`. STOMP endpoint at `/ws`. Test: `SessionEngineTest`.

Not yet built (Week 2, Sep 19–25 per the design doc): server-held timing/deadline enforcement,
question bank upload + option randomisation, MCQ auto-scoring.
