# Weekly contribution log — TEAM-AM

**Honest note.** We did not keep this log week by week during the semester. It was reconstructed on
2026-11-11 from the git history (`git log`) and `PROMPT_LOG.md`, with an AI assistant's help. Every line
can be traced to a commit or a log entry; anything we couldn't trace is left out. Module ownership follows design_doc §2.

| Week | Ajay (CS23B065): roster, question bank, scoring, attendance, analytics, export, dashboard | Mukesh (EE23B093): session engine, timing, resilience, integrity, doubts, load test |
|---|---|---|
| Sep 12–19 | Maven/Spring Boot scaffold, H2 schema, roster upload and verified join (`4ecadaf`); question bank, per-student option shuffle, MCQ scoring (`3a0d807`). | Server-held timing and deadline enforcement with a 300 ms grace window (`3a321e3`); reviewed the Week 2 build with Ajay (PROMPT_LOG 2026-09-19). |
| Sep 20–26 | Attendance derivation, first instructor dashboard, rotating-QR anti-proxy joining, laptop-only instructor actions (`9ade713`). | Resilient answers: failure-path design, tests first, then buffering/resend, deduplication and restart recovery, and the student page (`f51eb39`). |
| Sep 27–Oct 3 | Score-sheet export (.xlsx plus CSV with formula-injection protection), roster names, dashboard panels (`f5d0285`). | Integrity signals (advisory, capped), basic doubts channel, fix for the Release reply crash (`5434649`). |
| Oct 4–11 | Mid-evaluation (Oct 9). Comments on every function, folder READMEs (`f0f3388`), PROMPT_LOG (`2bb4b81`). | Mid-evaluation (Oct 9). No commits this week. |
| Oct 12–21 | Live tests on real phones; fixed rejoin with a stale saved identity and the default QR address (`1a240ea`); optional internet link for mobile data (`bfb7b40`). | Redesigned integrity signals after the phone tests: time away, recorded until the question closes, offline buffering (`540b2a2`). |
| Oct 22–Nov 1 | Typed-answer marking by the teacher (`c1c009f`); analytics with discrimination index and answer-key fix (`e0a8b3f`); question CSV upload, students' own marks, end-to-end smoke test (`9531650`); fixes found in screenshots (`45a4c94`). | Doubt upvoting and queue (`edc9deb`); never losing a student's answer, close question, end session, integrity switch (`6ba5c84`); load-test harness (`d058d0b`). |
| Nov 2–4 | QR speed setting and internet link on the dashboard (`8e8ff51`); questions with several correct answers (`573b6d4`); one-command class start (`08da5f2`); secret instructor link for the professor's own device (`0bf031d`, `3649403`, `0bcde14`); "Make live again" on the dashboard (`d1ddc08`). | Tap-to-save choices with the last tap before the deadline counting (`766d657`); choices unlock again after +30 s, take back an upvote (`beb722d`); make an earlier question live again (`8b6b37b`). |
| Nov 5–11 | Classroom rehearsal with about 40 students; final report, Docker image and handoff. | Classroom rehearsal with about 40 students. |

Commits made on a shared laptop are attributed to the module owner, who was present and approved them
(see PROMPT_LOG "commit attribution", 2026-09-19).
