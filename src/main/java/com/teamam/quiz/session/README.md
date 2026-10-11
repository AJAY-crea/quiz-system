# session/: running the lecture (Mukesh)

## Job 1: create a session
`SessionEngine.createSession` → new `quiz_sessions` row (status CREATED, current_question_order 0)

## Job 2: release a question, strictly in order
```
"Release next" → SessionEngine.release
  1. order must be current + 1, else refuse (no skipping)
  2. quiz_sessions: current_question_order = N, status = RUNNING
  3. questions.released_at = now          ← the timer starts here
  4. PUSH {"type":"RELEASED"} to every phone (live connection)
```

## Job 3: each phone asks "what should I show?"
```
phone gets the push → GET /api/sessions/{id}/state → SessionStateService.currentState
  → the open question, options in THIS student's order, time left (server clock),
    already answered?  NEVER the answer key
```
**Why push only "something changed"?** Each student has a different option order, so one
message can't fit everyone. And a phone that missed the push simply asks again when it reconnects.

## Job 4: the live entrance for answers
`LiveController.answer` receives an answer over the live connection → passes it to
`resilience/` → sends the ack back to **that phone only**.

## Files
| File | Role |
|---|---|
| `SessionController` | front door: create, release, extend (+30 s), state |
| `SessionEngine` | create + release + the push |
| `SessionStateService` | builds one phone's screen |
| `LiveController` | front door for live answers (not a URL) |
| `SessionEvent` | the tiny "RELEASED"/"EXTENDED" push message |
| `QuestionView`, `StudentQuestionView`, `SessionView`, `SessionStateView` | what screens receive (no answer key) |
| `CreateSessionRequest`, `NotAParticipantException` | data carrier / "not your phone" error (403) |
