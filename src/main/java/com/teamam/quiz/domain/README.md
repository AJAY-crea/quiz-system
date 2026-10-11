# domain/: the database tables

**One class = one table. One object of that class = one row. One field = one column.**
Spring (JPA/Hibernate) creates the tables from these classes when the app starts.

| Class | Table | One row means |
|---|---|---|
| `QuizSession` | `quiz_sessions` | one lecture's quiz |
| `Question` | `questions` | one question |
| `Participant` | `participants` | one student who joined, and from which phone |
| `Response` | `responses` | one student's answer to one question (max 1, enforced by the DB) |
| `Student` | `students` | a roll number + name (shared by all sessions) |
| `Doubt` | `doubts` | one doubt sent |
| `IntegrityEvent` | `integrity_events` | one "left the page / exited fullscreen / pasted" signal |

**Two tables without their own class** (they are lists inside a class):
- `session_roster` = the roll-number list inside `QuizSession`
- `question_options` = the options list inside `Question`

**Not tables, just allowed values for one column:** `QuestionType` (MCQ/TYPED), `SessionStatus` (CREATED/RUNNING/ENDED), `IntegrityEventType`.
