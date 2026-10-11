# roster/: who may join this lecture, and from which phone (Ajay)

## Job 1: upload the class list
```
CSV upload → RosterController.upload → RosterFileParser.parse ("CS23B065,Ajay" → roll, name)
           → RosterService.uploadRosterEntries → saves `students` + `session_roster`
```

## Job 2: rotating QR (stops WhatsApp-photo proxies)
```
"Open joining" → JoinWindowService.open → saves join_open_until + a secret in quiz_sessions
JoinCodes: code = HMAC(secret, current 15-second slot) → 8 characters, changes every 15 s
phone scans → POST /join-ticket → JoinWindowService.issueTicket
            → code current (or the previous one)? → TICKET valid 2 minutes
```
The ticket means slow typing after scanning doesn't matter.

## Job 3: join, with 4 rules in order (`RosterService.join`)
1. same roll number + same phone as before → let in immediately (reload / battery died)
2. ticket invalid → `REJECTED_INVALID_TICKET`
3. not on the roster → `REJECTED_UNLISTED`
4. phone used by another roll number (or roll number on a new phone) → let in but **FLAG**
→ `participantRepository.save(...)` writes the `participants` row

## How "same phone" is known
`student.js` creates a random **device token** on the first visit and keeps it in the phone's
browser storage (`quiz:device`), then sends it with every join. The server compares it with
`participants.device_token`.
Limits: a browser can't read the real phone ID; incognito, another browser or clearing data
gives a new token. That's why rule 4 only **flags** and never blocks.

## Files
| File | Role |
|---|---|
| `RosterController` | front door: upload, join |
| `JoinWindowController` | front door: open/close joining, join-ticket |
| `RosterService` | join rules + saving the roster |
| `JoinWindowService` | the window, checking codes, issuing tickets |
| `JoinCodes` | the maths for codes and tickets |
| `RosterFileParser` | reads the CSV |
| `JoinResult`, `JoinOutcome`, `JoinTicket`, `JoinRequest`, `RosterEntry`, `JoinWindowStatus`, `JoinRefusedException` | data carriers / the "refused" error |
