# Prompt Log — TEAM-AM quiz-system

AI assistant used: Claude Code (CLI), run from the project folder so it could read our proposal,
design doc and TA emails directly. Prompts below are quoted verbatim from the session transcripts
(typos included). "Technique" names the course concept the step actually used; "AI issues" records
what the assistant got wrong or what we had to decide ourselves.

Honest note on Weeks 1–2: our prompts were short and high-level. They worked because the design doc
already held the spec (module interfaces, test names, weekly plan), so the assistant built against
that document rather than against detailed prompts. From Week 3 we switch to explicit prompt
chaining (spec → tests → implementation → review) — see the Week 3 entries.

---

## 2026-09-19 — Status check and planning (Ajay)

**Prompt**
> so please read all the things of this project and design doc and yeah so today is 19th september
> so what should be done till now , but we didnt start anything , so should we start today ?

**Technique:** Context injection — the assistant read `proposal.pdf`, `design_doc.pdf`,
`previous_mails.md` and `project_details.md` instead of us summarising them in the prompt.

**Output:** Compared the design doc's weekly plan against the (empty) repo: the Sep 12–18 slice was
not started, mid-demo 3 weeks away, git history is graded per member. Recommendation: start today.

---

## 2026-09-19 — Reducing context cost, then Week 1 build (Ajay)

**Prompt**
> and also my doubt is that reading a pdf for u eveytime uses a tool and consumes lot of tokens
> right?? if it so then u can make them into .md files and read thats upto u and then, and yeah
> lets start with week1

**Technique:** Managing the context window (Module 1) — converted both PDFs to Markdown
(`design_doc.md`, `proposal.md`) so later sessions can re-read them cheaply. Then used the design
doc's Sep 12–18 row as the task spec.

**Mid-task refinements (same turn)**
> brooo can we have that quiz-system folder here

> and also be aware when to commit and who should commit

**AI issues / decisions**
- The assistant first created the repo as a sibling folder on the Desktop; we had it moved inside
  the project folder.
- It added a `Participant` entity that is not in the design doc's six-entity schema. We kept it:
  Roster's documented device-token mismatch flagging needs a per-session join record.
- No Maven on the machine — it added the Maven wrapper (`mvnw`) instead of asking us to install it.
- Commit attribution: Mukesh was not present, so the Week 1 commit (which includes his
  session-engine skeleton) is authored by Ajay only, and the commit message says so explicitly.

**Verification:** Tests named after the design doc's test plan — `rejectsUnlistedAndFlagsDeviceMismatch`,
`refusesEarlyRequestForNextQuestion` — plus an HTTP-level test uploading a 30-row CSV fixture.
`./mvnw test`: 3/3 passing.

---

## 2026-09-19 — Remote repository (Ajay)

**Prompts**
> where u comiitedd ,since he need to pull right

> tell me for this projects which is best since both need to make changes right

> https://github.com/AJAY-crea/quiz-system

**Output:** The commit was local-only; the assistant recommended one repo under Ajay's account with
Mukesh as a collaborator, added the remote and pushed.

---

## 2026-09-19 — Week 2 build, commit attribution (Ajay typing, Mukesh present and reviewing)

**Prompts**
> ok one thing what if that mukesh guys does it here itself becus u have whole conetext of project
> so better we do it here itself but we need to commit with his account , he gave me his pat token

> bro he is with me beside me sitting and watching, so lets do week 2 and commit with his account

**Technique:** Data-leakage awareness (Modules 1/7). The assistant advised against pasting Mukesh's
personal access token into the session. Commit authorship (name/email) is separate from push
authentication, so his commit was authored with
`git -c user.name=... -c user.email=... commit`, with no token involved.

**AI issues / decisions**
- Scope boundary: the assistant deliberately did not build answer submission/persistence, since
  that belongs to Resilience (Week 3, Mukesh).
- The IDE showed stale "unused import" warnings mid-edit; re-reading the files confirmed they were
  correct.
- Scoring test: the design doc's `reproducesHandComputedMarksheet` needs the full submission
  pipeline, so Week 2 has a narrower `scoresMcqAgainstShuffledAnswerKey`. The named test is still owed.

**Verification:** `recordsLateSubmissionAsTimeout` (Timing), `shuffledOptionMapsToCorrectKey`
(Randomisation, a plain unit test with no Spring context), and `ScoringServiceTest`.
`./mvnw test`: 7/7 passing.

**Commits:** timing module → authored by Mukesh; question bank, randomisation and scoring → Ajay.
Commit messages were drafted by the assistant (Module 3: AI-generated commit messages).

---

## 2026-09-26 — Mapping course concepts onto the remaining work (Ajay)

**Prompt**
> ok lets continue this and check till where we completed and from where we should continue now,
> and also new thing is that prof told to actually use the conecepts taught in the class while
> doing this project , and also he told he may ask for prompts we used...

**Output:** Weeks 1–2 are done and we are on schedule for Week 3 (Sep 26 – Oct 2). A student/instructor
frontend is not in the weekly plan but is needed for the mid-demo. Course techniques are mapped to
the remaining work: prompt chaining, negative prompting, chain-of-thought for the dedup race,
JaCoCo coverage, PMD/SpotBugs static analysis, feature branches, and `/code-review` as an audit step.

---

<!-- Week 3 entries continue below -->

## 2026-09-26 — Resilience, step 1 of 3: failure-path design (Mukesh; prompt drafted by the assistant)

**Prompt**
> Step 1 of 3 (design only, no code). Act as a senior backend engineer reviewing concurrency and
> failure handling. [...full text: context from design_doc §2 row 4, §3, §5 risk #4; four failure
> paths to reason through step by step; propose accept() check order, STOMP destinations, browser
> resend storage; constraints: server clock only, DB unique constraint as final dedup guard, no new
> libraries, no code; output: analysis, flow, open questions, 5-line plain-English summary]

**Techniques:** Role prompting, prompt chaining (step 1 of 3), chain-of-thought over explicit
failure cases, negative prompting (no client timestamps, no new libraries, no code), structured
output.

**Outcome:** failure-path analysis, a proposed accept() check order, STOMP destinations, a browser
resend store, and 8 open questions.

**Team decisions on the open questions (Ajay + Mukesh):** accepted all eight recommendations:
1. first submission is final;
2. a late resend is stored with onTime=false, excluded from scoring, and visible to the instructor;
3. manual "extend deadline by N seconds" for the instructor;
4. each question is judged by its own deadline;
5. submissions from a stale device token are rejected;
6. keep the 300 ms grace window;
7. add REJECTED to the outcome enum (goes on the mid-demo gap-list);
8. typed-answer normalisation is deferred to the typed-review module.

---

## 2026-09-26 — Resilience, step 2 of 3: tests first (Mukesh; prompt drafted by the assistant, approved by the team)

**Prompt**
> Step 2 of 3 (tests first, no production code). Act as a test engineer.
>
> Using the accepted design from step 1 and our decisions on its open questions, write JUnit 5
> tests that must fail until ResilienceService exists:
> 1. resentAnswerRecordedOnce — the same submissionId sent twice gives ACCEPTED, then DUPLICATE
>    (sameSubmission=true); exactly one Response row.
> 2. concurrentDuplicatesRecordedOnce — two threads submit at the same instant: exactly one
>    ACCEPTED, one DUPLICATE, one row. Must not run inside a test transaction, or the threads
>    cannot see each other's writes.
> 3. differentSecondAnswerIsDuplicateAndFirstStands.
> 4. lateAnswerStoredButMarkedLate.
> 5. rejectsStaleDeviceToken, rejectsUnreleasedQuestion, rejectsOutOfRangeOption.
> 6. extendDeadlineMovesCutoff.
> 7. resumesSessionAfterRestart — file-backed H2 in a temp directory, two Spring contexts one
>    after the other; after the restart the current question, the deadline and the saved answer
>    match, and a resend is DUPLICATE.
> 8. One STOMP integration test: a real client sends to /app/session/{id}/answer and receives
>    its ack on /user/queue/answer-ack (this checks that @SendToUser works without a login).
>
> Constraints: only what spring-boot-starter-test and spring-websocket already provide, with no
> new test libraries. Each test creates its own session so tests are independent. Give one line
> per test saying which failure it guards against.

**Techniques:** Prompt chaining (step 2), test-first, role prompting, negative prompting
(no new libraries, no production code).

---

## 2026-09-26 — Resilience, steps 3–4: implementation and browser resend (Mukesh; team instruction, prompts drafted by the assistant)

Team instruction to the assistant after approving step 2:
> go with all your recommendations, give step 2 prompt and add it to prompts and continue its work
> and this weeks work of muskesh ig and tell me once it completes

The assistant then ran the next two chained prompts itself; they are recorded here as drafted.

**Step 3 prompt (implementation)**
> Step 3 of 3. Implement ResilienceService.accept() and the supporting changes so that every
> step-2 test passes, following the step-1 check order exactly. Record arrival time first. Run the
> read-side checks in a read-only transaction. Do the insert in its own REQUIRES_NEW transaction
> and catch the unique-constraint violation outside it (a caught exception inside a Spring
> transaction marks it rollback-only). Do not refactor unrelated modules. After the tests pass,
> list every deviation from the step-1 design and why.

**Step 4 prompt (browser side of resilience)**
> Build the student page's answer path in plain HTML/JS served by Spring (no CDN, since the
> classroom network may have no internet, and no npm build). Keep unconfirmed answers in
> localStorage keyed by session and roll number, reusing one submissionId per answer across
> resends. Resend on every reconnect, and reconnect with backoff. After any reconnect, ask the
> server for the current state rather than trusting the page's own timer. Note any browser API
> that only works on HTTPS or localhost, because the classroom will use plain http:// over the
> LAN.

**Result:** 19/19 tests passing (12 new). Verified end to end against the real JAR with two
simulated phones driven by the page's own JS modules (Node.js), including killing and restarting
the server mid-question.

**AI issues found and fixed (debugging with AI, Module 4)**
- The STOMP ack test failed at first. The test's client handler swallowed errors, so the failure
  showed up as a silent 5-second timeout. We added error capture plus a check of whether the answer
  was stored. That showed the answer *was* stored and the ack was being dropped. Root cause: the
  broker was only enabled for `/topic`, and per-student acks go to `/queue`. One-line fix.
- The step-1 design proposed `@SubscribeMapping` for the state resync. Changed to REST
  `GET /api/sessions/{id}/state`, because each student needs their own option shuffle, and the
  broadcast was changed to carry only an event (`RELEASED` / `EXTENDED`), not the question.
- `crypto.randomUUID()` (the obvious choice) only works on HTTPS or localhost, so it would fail on
  the classroom LAN over plain http. We used `crypto.getRandomValues()` instead.
- Added `setPreserveReceiveOrder(true)` so one client's SUBSCRIBE is handled before its next SEND.
- Truncated `releasedAt` to milliseconds so the deadline read back after a restart equals the one
  computed before it.
- The concurrency test cannot force which guard catches the duplicate (the in-code pre-check or the
  DB constraint). In our run the DB constraint fired, as the logged unique-index violation shows.

**Not verified:** the page's DOM code (`student.js`) has not been clicked through in a real phone
browser yet. The STOMP client and answer buffer it uses were exercised against the real server.

---

## 2026-09-26 — Requirement refinement from trying the student page (Ajay)

**Prompt** (lightly edited for spelling and grammar; content unchanged)
> I opened student.html and it asks for a session ID. Wouldn't displaying a QR code be better?
> Also, the teacher should control a time window for joining — say after one minute it stops
> working, so students can't join with the same QR after that. And the first time someone uses a
> roll number, it should be saved in their browser's data, so that if they later use a different
> roll number we flag it, because that looks like proxy attendance.

**Technique:** Iterative refinement driven by using the product; asking the AI to critique a
human-proposed requirement before building it.

**AI critique (what changed our plan)**
- The QR must carry the laptop's LAN address, not `localhost`, and laptops often have several
  network adapters, so the instructor should see and confirm the address.
- A projected QR can be photographed and forwarded. A rotating QR (new join code every ~15 s)
  limits this; the join window alone does not.
- The window should block only *new* joins. A student who already joined must be able to reload
  or reconnect after it closes, and the teacher needs a "reopen" button for genuine latecomers.
- The per-browser ID is defeated by incognito mode, cleared data or a second phone, and IP
  addresses don't help because the whole class shares one behind the classroom Wi-Fi. So a
  cross-session roll-number change is **flagged for review, not blocked**, consistent with the
  design doc's "signals, not penalties".

**Team decision:** adopt all three as refinements of the proposal's "roster-verified joining" and
"same-device flag" (roster/attendance/dashboard, Ajay's modules), and list them in the mid-demo
gap-list.

---

## 2026-09-26 — Join rules + attendance, step 1 of 3: spec (Ajay; prompt drafted by the assistant, approved by Ajay)

**Prompt**
> Step 1 of 3 (spec only, no code). Act as a senior Java reviewer on this project, focused on
> anti-proxy attendance.
>
> Context: design_doc.md §2 rows 1 (Roster) and 8 (Attendance), the Attendance row of the §3 test
> plan, and PROMPT_LOG.md's latest entry (QR join window, rotating code, cross-session roll-number
> flag). Build on the existing RosterService.join, the Participant entity and its flag, and
> ResilienceService's stored Responses.
>
> Specify three things:
> 1. Join window: the instructor opens joining for N seconds, can close it or reopen it. Only NEW
>    joins are blocked when closed; an existing participant rejoining from the same device must
>    always succeed.
> 2. Rotating join code: a short code that changes every ~15 s and must be presented at join
>    time. Define how long an old code stays valid, so a student who scanned just before the
>    rotation isn't refused.
> 3. Cross-session proxy flag: if a device token previously joined ANY session under a different
>    roll number, flag the join for instructor review. Never block it.
>
> Then specify AttendanceService.derive(sessionId): present iff the student answered >= 50% of
> released questions (threshold configurable); decide whether late answers count, and how every
> kind of flag appears in the output.
>
> Constraints: server clock only. Flags are advisory, never penalties. Don't change Mukesh's
> ResilienceService. No new libraries in this step. Output: the spec as markdown, a list of edge
> cases, numbered open questions for me, and a 5-line plain-English summary I can explain in a viva.

Ajay's instruction: "go with all your recommendations and continue and add that given prompt".
Ajay approved the assistant's recommended answers to its own open questions in advance.

**Spec outcome (key points)**
- **Scan vs. type problem found:** a 15 s code checked at join time would refuse a student who
  takes 40 s to type their roll number. So the scan and the join are split. Scanning exchanges
  the fresh code for a **join ticket** valid for 2 minutes, and the join itself needs the ticket.
  Plain-English rule: "scan while joining is open, then you have 2 minutes to enter your roll
  number."
- **Code validity:** codes rotate every 15 s. A code from the current or the previous 15 s slot
  is accepted (15–30 s of validity), so a forwarded photo goes stale quickly.
- **Stateless and restart-safe:** codes and tickets are HMAC-SHA256 over a per-session secret
  stored in the database, using only the JDK (no new library). Nothing in memory needs to survive
  a restart.
- **Join order:**
  1. The same roll number rejoining from the same device is always accepted, with no ticket
     needed.
  2. Otherwise a valid ticket is required, else REJECTED_INVALID_TICKET.
  3. Roster check.
  4. Same-session device flags (existing).
  5. New cross-session flag: this device joined an earlier session under a different roll number.
- **Security hole found:** every instructor endpoint (release, upload, join code, attendance) was
  reachable by any student on the Wi-Fi, so anyone could read the join code or release questions.
  Decision: instructor endpoints and the instructor page are **localhost-only** (the app runs on
  her laptop). Only join, join-ticket and state are open to phones.
- **Attendance:**
  - Present iff answered ≥ ceil(threshold × released), with threshold 0.5 configurable.
    **Late answers count toward attendance** (they prove presence) though not toward score.
  - If no question has been released yet, attendance falls back to "joined".
  - Flags are listed per student and never change the status.
  - Rows are in roll-number order.

**Open questions → decisions (assistant's recommendations, approved)**
1. Ticket lifetime: 2 minutes.
2. Previous-slot code accepted: yes (one slot).
3. Late answers count for attendance: yes.
4. No questions released: fall back to joined.
5. After the window closes, a known roll number from a *new* device is refused, and the teacher
   can reopen.
6. Instructor endpoints: localhost-only.
7. Students are told that the roll number is linked to the phone (transparency), without
   explaining how the check works.
8. A student changing phones between sessions is not flagged (too noisy); only one device used
   for different roll numbers is flagged.

---

## 2026-09-26 — Join rules + attendance, steps 2–3; dashboard (Ajay; prompts drafted by the assistant under the instruction above)

**Step 2 prompt (tests first)**
> Step 2 of 3 (tests first, no production code). Act as a test engineer. From the approved spec,
> write JUnit 5 tests that fail until the feature exists:
> - pure unit tests for code rotation (current and previous slot accepted, older rejected, other
>   session rejected) and ticket expiry and tampering;
> - service tests: no ticket while closed, close stops new tickets, reopen works;
> - roster tests: refusesNewJoinWithoutValidTicket, allowsSameDeviceRejoinAfterWindowCloses,
>   flagsDeviceUsedByDifferentRollNoInEarlierSession (flagged, not blocked); update
>   rejectsUnlistedAndFlagsDeviceMismatch for tickets;
> - producesExpectedPresentAbsentList from the design doc (4 released questions, a 50% student,
>   a late answerer, a joined-but-inactive student, a never-joined student, two dual-device
>   joins);
> - a filter test proving instructor endpoints return 403 from a LAN address while join, ticket
>   and state stay reachable.
>
> No new test libraries. One line per test on the failure it guards against.

**Step 3 prompt (implementation)**
> Step 3 of 3. Implement the spec so every step-2 test passes. HMAC via javax.crypto only.
> Constant-time comparison for codes and tickets. Keep the existing join flags working. Don't
> modify ResilienceService. Then list every deviation from the spec.

**Dashboard prompt**
> Build the first instructor dashboard screen as plain HTML/JS served by the app (localhost-only):
> create a session, upload the roster CSV, add questions, open/close/reopen joining with a rotating
> QR (drawn on the server with ZXing core, Apache-2.0, as SVG; the laptop's LAN address is
> selectable because laptops have several adapters), the join code in text as a fallback, release
> the next question, extend its deadline, live joined/answered counts, and the attendance table
> with flags. DashboardController stays thin: composition only, no business logic (design doc
> §2 row 13). Update the student page so a scanned QR pre-fills the session, exchanges the code
> for a ticket immediately, and pre-fills the last roll number used on this phone.

**Result:** 38/38 tests passing (19 new or updated). One test decodes the generated QR back to its
text to prove it carries the right join link. Also verified in a real headless Chrome driven over
the DevTools Protocol:
- The dashboard created a session, uploaded a roster and added questions.
- Joining was opened and the QR rendered.
- A phone-sized tab opened the QR link on the laptop's real LAN address, joined, got its own
  option order, and answered ("Answer saved ✓").
- The phone was refused instructor endpoints (403).
- A second roll number from the same phone was flagged in the attendance table.
- No JavaScript errors on either page.

**Bugs found by the tests (Module 4: debugging with AI)**
- `Question.setOptions` kept the caller's list. With an immutable `List.of(...)`, Hibernate crashed
  when saving in the same transaction. A Week 2 bug, surfaced by the attendance fixture; fixed with
  a defensive copy.
- `findBySessionIdAndDeviceToken` returned a single result, but after one shared-phone join two
  participants share a token, so the next join from that phone would have thrown. A Week 1 bug,
  found while reading the code for the proxy feature; changed to a list.
- `JoinRefusedException` had no HTTP mapping, so a phone would have seen a 500 instead of "QR code
  expired". Added a 403 mapping.
- My own mistake during browser testing: I started the server and Chrome in parallel, and the
  server command's cleanup deleted Chrome's profile folder. Re-ran them sequentially.

**Deviations from the spec:** none in behaviour.
- The rotating code is 8 hex characters.
- The instructor QR screen also shows the code as text as a typed fallback.
- Changes to Mukesh's files were signature-only: `ResilienceServiceTest` and the shared `TestQuiz`
  fixture now pass a join ticket.
- The address list offered two adapters on this laptop (`10.22.13.61` wired, and a VirtualBox
  `192.168.56.1`), which confirms the reason for making it selectable.

---

## 2026-10-03 — Week 4 prompts generated by meta-prompting (Ajay; prompts generated by the assistant, verified by Ajay)

**Technique:** Meta-prompting (Module 2): the LLM writes the prompts for the next stage.
Ajay asked the assistant to generate this week's prompts from the syllabus and the design doc,
show them, and then run them while Ajay checks them.

**Meta-prompt**
> You are a prompt engineer for a CS5013 student project. Inputs: course_details.md (the
> syllabus), design_doc.md §4 row "Oct 3–9", and the current codebase. For each Week 4
> deliverable, write a prompt chain (spec → tests first → implementation → audit). Each prompt
> must name the course technique it uses, constrain the libraries to the approved stack, and end
> with a plain-English summary for the viva.

**Generated prompt A — Ajay, score-sheet export** (role prompting, negative prompting, Module 5
testing, Module 7 security)
> Act as a senior Java engineer who has shipped Excel exports to non-technical users. Implement
> ExportService.export(sessionId) per design_doc §2 row 11. One row per rostered student in
> roll-number order, including absentees. Columns: roll number, name, one per question, total,
> attendance, flags. MCQ mark = 1 only if correct AND on time. Typed answers show "review" (no
> review screen yet). Tests first: producesValidRosterOrderedXlsx, which must reopen the
> generated file with POI (risk #5). Also add the plain-CSV fallback, and protect it against
> spreadsheet formula injection (a name starting with "="). Use Apache POI only, no new libraries.

**Generated prompt B — Mukesh, integrity signals** (chain-of-thought, Module 7 ethics/bias)
> Act as a reviewer worried about false accusations. First reason step by step about which phone
> events cause tab-switch/blur signals in innocent cases (screen lock, notifications, call). Then
> implement capture: Page Visibility, blur, fullscreen exit, paste, sent only while a question is
> open. Use navigator.sendBeacon, because a page being hidden may never get to finish a normal
> request. The server verifies the device and caps how many events it stores. Test first:
> attributesBlurEventWithoutAlteringScore. Signals are advisory and never touch scores.

**Generated prompt C — Mukesh, doubts channel (basic)** (role prompting for privacy, negative
prompting)
> Act as a privacy reviewer. Build the basic doubts channel: a student submits a doubt tagged to
> the open question; the instructor sees a live list, can dismiss, can toggle author visibility
> (hidden by default), and can disable the channel. Do NOT build upvoting or the queue yet
> (Oct 10+). Authors must never reach other students. Cap length and doubts per student.
> Tests first.

**Generated prompt D — both, audit** (multi-agent generate/test/audit, Modules 6/8)
> Act as an independent auditor. Review this week's diff for bugs, security holes and untested
> paths, then fix what you find.

**Result:** 49/49 tests passing (11 new). The score-sheet test reopens the generated .xlsx with POI,
as risk #5 requires. Verified in headless Chrome over the laptop's LAN address:
- The phone answered, sent a doubt, and triggered a focus-loss signal.
- The dashboard showed the doubt as "Q1", anonymous by default.
- The dashboard showed "CS23B065 Q1 left the page 1".
- The score sheet downloaded as a valid .xlsx.
- No JavaScript errors.

**Audit findings (prompt D)**
- **Real bug, present since Week 1:** clicking Release showed "Internal Server Error" even though
  the release worked. The reply was built from a question whose options hadn't been loaded, after
  the database session had closed (lazy loading). Found by *looking at the dashboard screenshot*:
  the text checks all passed.
- **Why no test caught it:** every HTTP test was `@Transactional`, which keeps the database session
  open for the whole test and so hides this exact bug. We added a regression test *without* a
  transaction (`releaseEndpointReturnsTheQuestionWithItsOptions`), confirmed it failed with 500,
  then fixed it: question options load with the question, and the reply copies them.
- The same class of problem was checked across this week's endpoints (dashboard, attendance,
  export, doubts, integrity). Each reads inside its own transaction, so they are not affected.
- **Schema-migration trap avoided:** the new session switches are nullable columns, because
  `ddl-auto=update` cannot add a NOT NULL column to a table that already has rows (an existing
  `data/` database).
- **Spreadsheet formula injection:** a roster name such as `=HYPERLINK(...)` would run as a formula
  in the CSV fallback. Fixed by escaping, with a test. The .xlsx is safe by construction, because
  cells are written as text.

**Decisions taken from the generated prompts (for Ajay and Mukesh to verify)**
- A late MCQ answer scores 0 even if correct. Typed answers show "review" and are excluded from
  Total until the typed-review module exists.
- Integrity signals are sent only while a question is open *and unanswered*. A switch-away fires
  both "blur" and "hidden", so it is counted once (2 s debounce). Storage is capped at 50 per
  student per question.
- A doubt is tagged with whichever question is current when it is sent. Each student can send at
  most 10 doubts per lecture, each at most 500 characters.

---

## 2026-10-08 to 10-11: understanding the codebase for the mid-evaluation (Ajay)

**Prompts** (verbatim)
> ok before that i need to do one thing ,i need to see how everything works a to z , i forgot
> everrything so better i will read all ... please give me .md file ... and also how about comments
> in each file like what each function does ...

> so domain are just classes defined foe each data we want to store right so basically a row of
> each table is a instance of each class we defined in each file of doamin folder right!! ok that i
> understood and next thing is what , lets do one thing lets talk like this and dont take up more
> tokens please

**Technique:** AI as a tutor via Socratic, step-by-step explanation (one folder at a time, with a
check question after each), plus AI-generated documentation (Module 3).

**Output**
- A one-line comment above every function (143 Java, 40 JS). The diff was verified to contain
  only added comment lines, with no code changes, and all 49 tests still pass.
- A `README.md` inside each package folder explaining what it handles (written as we went through
  the folders together).

**AI issues / decisions**
- The assistant first built a live "code trace" page (a dev-only feature that showed which Java
  methods ran for each click). It worked and was tested, but I found it harder to follow than a
  plain explanation and it added a dependency I would have to defend, so I asked for it to be
  removed. It was fully reverted (49 tests passing, no trace code left).
- A walk-through it wrote claimed that reloading the phone page re-runs the join. Reading
  `student.js` showed the page restores the saved identity instead and never calls join, so the
  walk-through was corrected before I used it.

---

## 2026-10-12 to 10-21: live testing on real phones, and the mid-evaluation (Ajay + Mukesh)

**What we did:** ran the full app on the laptop, with real phones as students, following our own
test checklist (join by QR, release, answer, airplane mode, late answer, doubts, signals, export,
restart). Each thing that looked wrong was given to the assistant as a bug report, with a phone
screenshot where we had one.

**Prompts (verbatim)**
> [phone screenshot: "This roll number has been joined from another device"] Why is this happening.
> And also it says 0 joined

> and also integrity signals are not working , i tested like sitiching off wifi and also leaving the
> page and all,it didnt show on instructor page, remember this issue

> the link in the phone isnt working , can u check one

**Technique:** AI-assisted debugging (Module 4): a symptom plus evidence; the AI first reads the
code to explain the cause, then reproduces it in isolation before any fix.

**Findings (recorded in KNOWN_ISSUES.md)**
1. *"Joined from another device", 0 joined:* a **real bug**. The phone had a saved identity for
   "session 1" from an older database; the fresh database also starts at session 1, so the page
   skipped Join, the server answered 403 "not joined", and the page showed one generic message for
   every 403.
2. *Signals not showing:* reproduced with a scripted phone, and the **pipeline worked**. Our manual
   test broke the rules on purpose-built conditions: Q1 was already answered, Q2's time was over,
   and Wi-Fi was off. One **real limitation**: signals made while offline are lost (not buffered).
3. *Link not opening:* the Ethernet cable had been unplugged, so its address vanished, and the
   dashboard listed the **VirtualBox** adapter first, which became the QR's default address that
   no phone can reach.

**Mid-evaluation:** the live modification tasks were practised with Gemini using our
`GEMINI_CONTEXT.md` summary. The practice changes ("even roll numbers see the instructor's order,
odd ones reversed"; "latest answer wins") were reverted afterwards (`git checkout -- .`), and
all 49 tests passed again.

---

## 2026-10-21 — Fixes: rejoin on the same phone, classroom-address default (Ajay)

**Prompt (verbatim)**
> yeah i faced that rejoining on same phone has some issues we need to see that ... and then that
> dashboard thing for wifi any issue there??

**Refined prompt (drafted by the assistant, approved by Ajay)**
> Fix two bugs from KNOWN_ISSUES.md without changing the join rules.
> (1) student.js: when GET /state returns 403 with "has not joined this session", forget the saved
> identity and return to joining. If the page was opened from a fresh QR, reuse its code so the
> student only presses Join; otherwise show the join form with the session number filled in and a
> clear message. Keep the "joined from another device" message for the other 403 case.
> (2) DashboardController.hostAddresses: hide adapters made by VM/container software (VirtualBox,
> VMware, Hyper-V, WSL, Docker) and order the rest hotspot → Wi-Fi → cable, so the default QR
> address is reachable. Add a unit test using the real adapter names from this laptop.
> Constraints: no new libraries; all existing tests must still pass. Verify (1) in a real browser by
> planting a stale identity.

**Techniques:** a negative constraint (don't change the join rules), a test from real observed data
(adapter names), verification by reproduction.

**Result:** 50/50 tests (1 new: `ClassroomAddressTest`). Browser check: a stale identity + a new
QR → back to Join with the roll number pre-filled → joined (count 0 → 1); without a QR → the join
form with the session number and the message; no JS errors.

---

## 2026-10-21 — Integrity signals, redesigned (Mukesh; Ajay typing, Mukesh present and deciding)

**Prompt (verbatim, Ajay with Mukesh)**
> integrity singals are lost i mean for now its ok right like when he didnt answer the present
> question it records after answering also we should record becus students should neveer go to
> other tab during this process , i think that would be fair , in that case smtg like screen off
> will also be recorded which is not ok, we need to have a better wya for this

**AI critique first (before any code):** a web page **cannot** tell "switched app" from "screen
turned off": the browser reports both only as "page hidden". So the assistant proposed recording
*how long* the page was hidden instead of a bare count, and five independent steps for us to choose.

**Refined prompt (drafted by the assistant, approved by Mukesh: all 5 steps)**
> Act as a reviewer worried about false accusations. Rework integrity signals (advisory only):
> 1. Record while the question is open, answered or not.
> 2. Measure how long the page was hidden: remember when it hid, report on return (also after a
>    page reload), and show "N× · S s away" on the dashboard.
> 3. Ignore a plain window "blur" (notification shade, system pop-up); count only "page hidden".
> 4. While a question is open, request a screen Wake Lock so the screen doesn't time out. It needs
>    HTTPS, so fail silently on plain http.
> 5. Keep unsent signals on the phone, like answers, and retry every 5 s, so going offline loses
>    nothing. The server should accept reports up to 2 minutes after the deadline, because they
>    are sent on return.
> Constraints: the phone-measured time away is advisory, like the signal; cap it. The new DB column
> must be nullable (existing databases). Signals still never touch marks. Tests first for the
> server; verify the phone behaviour in a real browser, including offline.

**Techniques:** asking the AI to critique a requirement before building; role prompting (false
accusations); negative constraints; tests first; verification by reproduction (offline emulation).

**Result:** 52/52 tests (new: `acceptsReportArrivingShortlyAfterDeadline`,
`summaryAddsUpTimeAwayPerStudentAndQuestion`; the "closed question" test now checks 2+ minutes after
the deadline). Browser check:
- away 3 s *after answering* → "1× · 3 s away";
- a plain blur → not counted;
- came back while offline → kept on the phone, delivered 5 s after reconnecting ("2× · 5 s away").

**Known limit (honest):** the Wake Lock only works on HTTPS (or localhost), so on the classroom's
plain-http address an automatic screen timeout still shows as "away". The time-away figure lets the
instructor judge.

---

## 2026-10-21 — Students on mobile data: optional internet link (Ajay)

**Prompt (verbatim)**
> and also making students to be on same network is not ok i guess they usually use celluar , so
> what to do??

**AI analysis (before code):** the laptop's address is private, so phones on mobile data can't
reach it. The laptop hotspot is no fix either: Windows allows only 8 devices. Options: a tunnel
(the app stays on the laptop and gets a public https link) or cloud hosting (needs an instructor
login and deployment). It flagged a **security hole a tunnel would open**: the tunnel program runs
on the laptop, so every tunnelled phone would look like "localhost", i.e. the instructor.

**Refined prompt (drafted by the assistant, approved by Ajay)**
> Support an optional public link without weakening security. Tests first:
> (1) InstructorOnlyFilter must treat any request carrying a proxy header (Forwarded,
> X-Forwarded-For, X-Real-IP, CF-Connecting-IP) as a phone, even from 127.0.0.1. Write the failing
> test first and confirm it fails.
> (2) A --quiz.public-url property; when set, the dashboard offers it first as the QR address.
> No new libraries in the app; document the Cloudflare quick-tunnel steps in the README.

**Techniques:** asking for options and risks before choosing; test-first with a test written to
expose the hole (it failed with 404, i.e. a tunnelled "phone" reached Release); negative
constraint (don't weaken security).

**Result:** 54/54 tests (new: `tunnelledRequestsAreNeverTreatedAsTheInstructor`,
`PublicLinkTest`). The live tunnel run is still to be done once cloudflared is installed.

---

## 2026-11-01 — Final-milestone features: the meta-prompt (Ajay and Mukesh, sitting together)

**Prompt (verbatim)**
> ok can we make proper changes more and commit as per that on both side ,we are sitting beside
> eachother and also write some nice metaprompts for that in prompts.md and also think about cases
> like for subjective questions where ppl write answers in the boxx and for that we dont do the
> markings and teacher does it after and also small features ,go through the design doc and see what
> we didnt implement and also what better features we can make , /eff

(We asked for "prompts.md"; the log the course sees is this file, so the prompts live here.)

**Technique:** meta-prompting (Module 2): the assistant writes the prompts, then executes them in the
same session. The prompts below were written *before* the code, and each result records what
actually happened.

**Meta-prompt (written by the assistant from the instruction above)**
> You are a prompt engineer and a sceptical tech lead for a CS5013 project due in five days.
> Inputs: design_doc.md (§2 modules, §3 tests, §4 "Oct 10 – Nov 5", §5 risks), the current code, and
> the team's new requirement that typed answers are marked by the teacher afterwards.
> Step 1. List everything the design doc promises that the code lacks; cite the section.
> Step 2. Walk through a real lecture and list what would hurt a student or the teacher, and check
> each against the code, not from memory.
> Step 3. Rank by how much it hurts, and drop whatever can't be built and tested properly in time.
> Step 4. For each surviving item write one prompt with: a role; decisions stated, not left to the
> implementer; negative constraints; the tests to write first (design-doc names where they exist);
> and how to verify beyond unit tests. Assign an owner from design_doc §2 so commits stay honest.
> Never invent results: a step not run stays "not run".

**Step 1 output: promised but missing**
| Design doc | Missing | Owner |
|---|---|---|
| §2 row 6, §4 | `groupTyped`: typed-answer review | Ajay |
| §2 row 10, §4 | Analytics module (none of it exists) | Ajay |
| §2 row 11, §4 | Export with analytics | Ajay |
| §2 row 5 | Question upload endpoint | Ajay |
| §3 | `reproducesHandComputedMarksheet`, `computesDiscriminationIndexOnFixture`, `endToEndSmokeRunsThreeQuestionQuiz` | Ajay |
| §2 row 9, §4 | Doubt upvoting and queue | Mukesh |
| §2 row 12, §4 | Load-test harness | Mukesh |
| §3 | `keepsAuthorsHiddenAndCountsUpvoteOnce`, `sustainsFullRosterConcurrentSubmit` | Mukesh |
| §5 risk 3 | Instructor can disable or clear integrity signals | Mukesh |
| §4 | Integrity calibration run, stakeholder dry-run | needs real people; cannot be done by the assistant |

**Step 2 output: found by walking through a lecture**
- *Typed answers can be wiped while typing (read from the code, to confirm in a browser).*
  `renderAnswerForm` clears the box on every state refresh, and a refresh happens on +30 s, on any
  reconnect, and on reload. A student writing a long answer loses it.
- A student who has typed or chosen an answer but not pressed Submit when time ends gets nothing.
- Over-long typed answers are refused by the server only after the form has gone.
- All marks are worth 1, so a 5-mark subjective question can't be expressed.
- A wrong answer key can't be corrected after the quiz, even though analytics exist to reveal one.
- The question can't be closed early when everyone has answered, the session can't be ended, and a
  typo in an unreleased question can't be removed.
- Students never see their marks.

**Step 3: dropped.** Editing an answer after submitting (first answer stands: design doc §2 row 4);
AI-assisted marking (a teacher asked to mark the answers; an LLM needs internet and sends student
text off the laptop).

### Generated prompt A — Ajay: teacher marks typed answers afterwards
(role prompting, chain-of-thought on the grouping rule, negative prompting, tests first)
> Act as a teacher who has 120 subjective answers to mark, and as a sceptical reviewer of your own
> grouping rule. Typed answers are never auto-marked. Build the `ScoringService.groupTyped` promised
> in design_doc §2 row 6.
> Reason first: which answers may safely share one mark? Over-grouping is the dangerous failure,
> because one mark lands on different answers. "V = IR." and "v=ir" are the same; "1 2" and "12", or
> "x+1" and "x-1", are not. Write those as tests before the code.
> Decisions: each question gets max marks (default 1, nullable column for existing databases). The
> teacher marks per group, and can override one student. Half marks are allowed; 0 to max only.
> Clearing a mark puts it back to waiting. Late answers score 0, are listed apart and can't be
> marked (design doc: late is excluded from scoring). Keep one rule for "what does this answer earn",
> used by every module. In the sheet, an unmarked answer stays "review" and out of Total; say how
> many are left. Hide roll numbers while marking by default, to reduce marking bias (Module 7).
> Do NOT: auto-mark, re-render the list on the 1-second dashboard refresh (it would wipe a mark being
> typed), or render student text as HTML.

**Result:** 60/60 tests (new: `TypedReviewTest` ×5, `typedMarksReachTheSheetAndTheTotal`). The screen
itself is checked in the browser run recorded at the end of this log.

### Generated prompt B — Ajay: analytics and the upgraded export
(few-shot through a hand-worked example, negative prompting, tests first)
> Act as an exam-board statistician explaining to a teacher who has never heard of a discrimination
> index. Build `AnalyticsService.forQuestion` per design_doc §2 row 10: % correct, discrimination
> index, distractor pull, kept across the semester.
> First work one example by hand: 20 students, 3 MCQs, top and bottom 27%. Put the working in the
> test's comment, so `computesDiscriminationIndexOnFixture` can be checked by a person. Then code.
> Decisions: rank only students who answered something, or absentees fill the bottom group. Break
> ties by roll number, so results don't change between runs. Withhold the index (don't print a
> confident-looking number) under 4 students or while typed answers are unmarked. A negative index
> warns "check the answer key", and there must be a way to fix the key and re-score everyone.
> Add "Question analysis" and "Typed answers" sheets to the .xlsx (§2 row 11, §4), and write
> `reproducesHandComputedMarksheet` from a table marked by hand *before* running the code.
> Do NOT: add a chart library (no internet in class), or show results on the 1-second refresh: the
> dashboard may be on the projector during a question.

**Result:** 66/66 tests (new: `AnalyticsServiceTest` ×4 including the design-doc test,
`reproducesHandComputedMarksheet`, `xlsxCarriesQuestionAnalysisAndTypedAnswers`). Both hand-worked
tables matched on the first run.

### Generated prompt C — Ajay: question upload, small fixes, students' own marks, smoke test
(prompt chaining from A and B, negative prompting, security-first review)
> Act as the professor preparing for a lecture the night before, then as a student trying to cheat.
> 1. CSV question upload (design_doc §2 row 5; session-scoped, since a question belongs to a
>    session). Quoted commas must work. Errors must name the line. All or nothing: one bad line adds
>    no questions.
> 2. Remove an unreleased question and close the numbering gap, or later releases will refuse.
> 3. Let a student see their own marks, off until the instructor publishes. As the cheating student:
>    what could this leak? Nothing about an open question (it would reveal the right answer to pass
>    on), no answer key, no one else's marks, and only to the phone that joined.
> 4. `endToEndSmokeRunsThreeQuestionQuiz` (§3): every step through the real endpoints, 10 students,
>    3 questions. Do NOT mark it @Transactional: that hid the Week 4 lazy-loading crash.
> 5. Prove every new instructor endpoint refuses a phone, and a tunnelled phone.

**Result:** 74/74 tests (new: `QuestionUploadTest` ×4, `MyMarksServiceTest` ×2,
`endToEndSmokeRunsThreeQuestionQuiz`, `markingAndAnalyticsRefuseLanAddresses`).

### Generated prompt D — Mukesh: doubt upvoting and queue
(role prompting for privacy, negative prompting, tests first)
> Act as a privacy reviewer and as a student who wants to game the system. Build upvoting and the
> queue per design_doc §2 row 9. Students see open doubts and upvote each once; the instructor's
> queue puts the most upvoted first, then oldest.
> Enforce "once" with a database unique constraint (doubt, roll number), not only a check in code:
> the same reasoning as design doc risk 4. Increment inside the database so two votes can't overwrite
> each other. Refuse your own doubt, a dismissed doubt, and any phone but the one that joined.
> Anonymity: the student reply type must have no author field at all, so nothing can leak by
> accident; prove it by searching the serialised JSON for the roll number.
> Sharing doubts with classmates brings a new risk the design doc doesn't cover: an abusive anonymous
> message reaches the whole class. Give the instructor a switch, and make dismissing remove it.
> Phones poll this, so not every second. Do NOT render doubt text as HTML.

**Result:** `keepsAuthorsHiddenAndCountsUpvoteOnce` and 3 further tests pass; see the commit.

### Generated prompt E — Mukesh: never lose what a student wrote; instructor controls
(chain-of-thought over failure paths, negative prompting)
> Act as a student halfway through a long typed answer in a bad classroom network. List every event
> that redraws the answer form (+30 s, reconnect, reload, any broadcast), and what happens to the text
> each time. Then fix it: redrawing must never wipe the form, and the draft survives a reload, kept
> on the phone like unsent answers. Selected MCQ options too.
> A student who has written or chosen something but not pressed Submit when time ends must not get
> nothing: send it automatically, early enough to arrive on time, and say so. Stop the box at the
> server's limit, with a counter, instead of refusing the answer after the form has gone.
> Add instructor controls: close a question early (with 5 s notice so phones can send drafts) and end
> the session (refused while a question is open; releasing again reopens). Do not add new deadline
> logic: closing must flow through the same deadline so restart recovery still works.
> Design doc risk 3 promises the instructor can disable or clear integrity signals: build that.
> Every new instructor action must refuse a phone, with a test.

**Result:** tests pass (new: `closeSoonMovesTheDeadlineToAFewSecondsFromNow`,
`endIsRefusedWhileAQuestionIsOpenAndReleasingReopens`, `instructorCanStopRecordingAndClearSignals`,
`sessionControlAndIntegritySettingsRefuseLanAddresses`). Browser check with 6 scripted phones:
- **The bug was real.** Serving the previous `student.js` to one phone, +30 s emptied its box while it
  held a half-written answer. The new page kept the text, and a reload restored it.
- A draft left unsubmitted was sent automatically, both on "close now" and at a natural deadline, and
  arrived on time.

**Unexplained result (honest):** one run of the browser script scored 23/26, right after a stuck test
browser had been force-killed. The output had been cut off, so which 3 checks failed was not recorded.
The next 4 runs were all 26/26. The suspected cause is the script's fixed 800 ms wait, which was then
replaced by waiting for the question to appear (it appeared in about 60 ms), but this is not proven.
In the code, a phone connecting late still asks the server for the current question on connect.

### Found by looking at the browser screenshots (Ajay)
The scripted checks all passed; these only showed up by reading the images, the same lesson as Week 4.
- A student's marks left out a question the instructor had moved on from until its timer ran out, so
  the total looked wrong. An earlier question now counts as closed (test first).
- The CSV format hint and the address picker overflowed their cards.
- Discrimination on an MCQ is ranked on marks so far, so it shifts while typed answers are unmarked,
  and rests on one student per group in a class of 5. The dashboard now says both, instead of
  showing "1.00" as if it were solid.

### Generated prompt F — Mukesh: load-test harness
(role prompting, negative prompting, honest reporting)
> Act as a performance engineer who distrusts flattering numbers. Build
> `LoadTestRunner.run(sessionId, clientCount)` -> `LoadReport` per design_doc §2 row 12: one simulated
> client per rostered student, not surfaced on the dashboard. Each client joins over HTTP and opens
> its own real WebSocket; do not call the service directly, which would skip the part most likely to
> break. Everyone submits at once, and each sends the answer twice, so dedup is loaded as well.
> Check the database afterwards: a reply saying "accepted" is not proof it was stored once.
> Add a command-line mode that refuses any database that isn't clearly a throwaway, since it writes
> hundreds of fake students. State the limit in the report's documentation: it all runs on one laptop,
> so it does not test the classroom Wi-Fi. The class size is unknown (design doc: "fixed once we have
> her class list"), so say which number we chose and why.

**Result:** `sustainsFullRosterConcurrentSubmit` passes with 150 clients. Command-line runs of the
packaged jar with a database on disk (as in class), on this laptop:

| Clients | Accepted on time | Resends recognised | Stored | Median reply | Slowest reply |
|---|---|---|---|---|---|
| 100 | 100 | 100 | 100 | 71 ms | 116 ms |
| 300 | 300 | 300 | 300 | 119 ms | 172 ms |
| 600 | 600 | 600 | 600 | 182 ms | 302 ms |

**What this does not show:** Wi-Fi or hotspot capacity (a Windows hotspot allows only 8 devices), real
phones, or slow connections. The client and server share one machine, so the figures are for the server
and database only. Design doc Plan B (reduced, honestly reported device count) still applies.

**AI issue:** the first draft called a Java method that does not exist
(`Thread.startVirtualThreadOrPlatform`), a hallucination. Spotted on review before compiling.

### Final check, and what is still open
83/83 tests. Browser script with 6 scripted phones and the dashboard: 26/26, with no JavaScript errors, and
the downloaded .xlsx has all four sheets. Every test named in design doc §3 now exists.

**Not done, because it needs real people:** the integrity calibration run, the stakeholder dry-run, and a
test with real phones on a real network. "Phones" above are separate browser profiles on one laptop.
