# repository/: the only way in and out of the database

One file per table. Services call these; controllers never touch the database (one exception: `DashboardController` reads directly to draw the screen).

**Reading:** we write only a method NAME; Spring builds the SQL from it.
```
countBySessionIdAndQuestionId(1, 20)
count | By | SessionId | And | QuestionId
→ SELECT COUNT(*) FROM responses WHERE session_id = 1 AND question_id = 20
```
- The table comes from `extends JpaRepository<Response, Long>`: Response → `responses`.
- The start word is the action: `find` = rows, `count` = number, `exists` = true/false.
- A misspelt field name means the app refuses to start.
- `@Query("...")` = a query written by hand when a name isn't enough (e.g. count per student).

**Writing:** NOT in these files. The methods are inherited free from Spring's `JpaRepository`:
`save(obj)`, `saveAndFlush(obj)`, `delete(obj)`, plus `findById`, `findAll`, `count`.
(Ctrl+click `JpaRepository` in VS Code to see them.)

**How a write works:** build a domain object (= a full row), then `save(object)`.
- object has no `id` yet → **INSERT** (the DB gives it an id)
- object was loaded with `find...` (has an `id`) → **UPDATE**

The services decide WHEN to read or write; the repository does it.
