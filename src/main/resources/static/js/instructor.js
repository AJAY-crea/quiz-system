// Instructor dashboard: every action calls the owning module's endpoint; this page only displays.
const $ = (id) => document.getElementById(id);
const POLL_MS = 1000;

let sessionId = null;
let dashboard = null;
let shownQrKey = null;

// ---------- helpers ----------

// Call a server endpoint; throw a readable error if it fails.
async function api(method, url, body) {
  const options = { method };
  if (body instanceof FormData) {
    options.body = body;
  } else if (body !== undefined) {
    options.headers = { 'Content-Type': 'application/json' };
    options.body = JSON.stringify(body);
  }
  const response = await fetch(url, options);
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(data?.error ?? `${method} ${url} failed (${response.status})`);
  return data;
}

// Show a status or error line at the top of the dashboard.
function say(message, isError = false) {
  $('status').textContent = message;
  $('status').className = isError ? 'error' : '';
}

// Do a button's action, then refresh the dashboard; show any error.
async function run(action) {
  try {
    await action();
    await refresh();
  } catch (e) {
    say(e.message, true);
  }
}

// A table cell with plain text (textContent, so student text can never inject HTML).
function cell(text) {
  const td = document.createElement('td');
  td.textContent = text;
  return td;
}

// A small coloured label, e.g. PRESENT / ABSENT.
function pill(text, kind) {
  const span = document.createElement('span');
  span.className = `pill ${kind}`;
  span.textContent = text;
  return span;
}

// ---------- sessions ----------

// Fill the session picker and reopen the last session used.
async function loadSessions() {
  const sessions = await api('GET', '/api/dashboard/sessions');
  const select = $('session-select');
  select.replaceChildren(new Option('Choose a session…', ''));
  for (const s of sessions) select.append(new Option(`#${s.id} · ${s.title}`, s.id));
  const remembered = localStorage.getItem('quiz:instructor-session');
  if (remembered && sessions.some((s) => String(s.id) === remembered)) {
    select.value = remembered;
    selectSession(remembered);
  }
}

// Switch the dashboard to a session.
function selectSession(id) {
  sessionId = id || null;
  shownQrKey = null;
  $('panels').hidden = !sessionId;
  if (sessionId) localStorage.setItem('quiz:instructor-session', sessionId);
  $('attendance').replaceChildren();
  $('attendance-summary').textContent = '';
  $('review-groups').replaceChildren();
  $('review-late').hidden = true;
  $('review-summary').textContent = '';
  reviewQuestionId = null;
  $('analytics').replaceChildren();
  refresh();
}

// ---------- rendering ----------

// Every second: reload counts, the QR, the question table and doubts (signals every 5 s).
async function refresh() {
  if (!sessionId) return;
  try {
    dashboard = await api('GET', `/api/sessions/${sessionId}/dashboard`);
  } catch (e) {
    say(e.message, true);
    return;
  }
  $('roster-size').textContent = dashboard.rosterSize;
  $('joined').textContent = dashboard.joined;
  $('flagged').textContent = dashboard.flagged;
  renderJoin(dashboard.join);
  renderQuestions(dashboard.questions);
  renderReviewPicker(dashboard.questions);
  $('marks-visible').checked = dashboard.session.marksVisible;
  $('download-xlsx').href = `/api/sessions/${sessionId}/export.xlsx`;
  $('download-csv').href = `/api/sessions/${sessionId}/export.csv`;
  try {
    await renderDoubts();
    if (Date.now() - lastIntegrityRefresh > 5000) await renderIntegrity();
  } catch (e) {
    say(e.message, true);
  }
}

let lastIntegrityRefresh = 0;

// The doubts list (open first) with Dismiss buttons and the two switches.
async function renderDoubts() {
  const panel = await api('GET', `/api/sessions/${sessionId}/doubts`);
  $('doubts-enabled').checked = panel.enabled;
  $('doubts-authors').checked = panel.authorsVisible;
  $('doubts-shared').checked = panel.shared;
  const open = panel.doubts.filter((d) => !d.dismissed).length;
  $('doubt-count').textContent = open ? `${open} open` : '';
  $('doubts-empty').hidden = panel.doubts.length > 0;
  const list = $('doubts');
  list.replaceChildren();
  for (const d of panel.doubts) {
    const li = document.createElement('li');
    if (d.dismissed) li.className = 'dismissed';
    const body = document.createElement('div');
    const meta = document.createElement('div');
    meta.className = 'muted';
    meta.textContent = [`▲ ${d.upvotes}`, d.questionOrder ? `Q${d.questionOrder}` : 'general', d.authorRollNo].filter(Boolean).join(' · ');
    const text = document.createElement('div');
    text.className = 'text';
    text.textContent = d.text;
    body.append(meta, text);
    li.append(body);
    if (!d.dismissed) {
      const dismiss = document.createElement('button');
      dismiss.className = 'secondary';
      dismiss.textContent = 'Dismiss';
      dismiss.addEventListener('click', () => run(() => api('POST', `/api/sessions/${sessionId}/doubts/${d.id}/dismiss`)));
      li.append(dismiss);
    }
    list.append(li);
  }
}

// The advisory signals table, with friendly labels.
async function renderIntegrity() {
  lastIntegrityRefresh = Date.now();
  const rows = await api('GET', `/api/sessions/${sessionId}/integrity`);
  // A page can't tell "switched app" from "screen off", so the label says both and the time away helps judge.
  const labels = { FOCUS_LOSS: 'left the page / screen off', FULLSCREEN_EXIT: 'exited full screen', PASTE: 'pasted text' };
  const body = $('integrity');
  body.replaceChildren();
  for (const r of rows) {
    const tr = document.createElement('tr');
    tr.append(cell(r.rollNo), cell(`Q${r.questionOrder}`), cell(labels[r.eventType] ?? r.eventType),
      cell(r.eventType === 'FOCUS_LOSS' ? `${r.count}× · ${r.awaySeconds} s away` : `${r.count}×`));
    body.append(tr);
  }
  $('integrity-empty').hidden = rows.length > 0;
}

// Show the QR + code while joining is open (redraw only when the code changes).
function renderJoin(join) {
  const img = $('qr');
  if (!join.open) {
    img.hidden = true;
    $('code').textContent = '';
    $('join-state').textContent = 'Joining is closed. Students already joined can still reconnect.';
    shownQrKey = null;
    return;
  }
  const base = $('base-url').value;
  const key = `${join.code}|${base}`;
  if (key !== shownQrKey && base) {
    img.src = `/api/sessions/${sessionId}/join-qr.svg?base=${encodeURIComponent(base)}&v=${join.code}`;
    shownQrKey = key;
  }
  img.hidden = !base;
  $('code').textContent = `${sessionId} · ${join.code}`;
  $('join-state').textContent =
    `Open for ${join.secondsLeft}s more · code changes in ${Math.ceil(join.codeRotatesInMs / 1000)}s`;
}

// The live-quiz table: open (time left) / done / not released, and answers received.
function renderQuestions(questions) {
  const body = $('questions');
  body.replaceChildren();
  const current = questions.find((q) => q.remainingMs !== null);
  for (const q of questions) {
    const tr = document.createElement('tr');
    tr.append(cell(q.orderIndex), cell(q.text), cell(q.maxMarks));
    const state = document.createElement('td');
    if (q === current) {
      state.append(q.remainingMs > 0 ? pill(`open · ${Math.ceil(q.remainingMs / 1000)}s left`, 'ok') : pill('time up', 'warn'));
    } else {
      state.append(q.released ? pill('done', '') : pill('not released', ''));
    }
    tr.append(state, cell(q.released ? `${q.answered} / ${dashboard.joined}` : '—'));
    const actions = document.createElement('td');
    if (!q.released) {
      const remove = document.createElement('button');
      remove.className = 'secondary';
      remove.textContent = 'Remove';
      remove.addEventListener('click', () => run(async () => {
        await api('DELETE', `/api/sessions/${sessionId}/questions/${q.id}`);
        say(`Removed question ${q.orderIndex}.`);
      }));
      actions.append(remove);
    }
    tr.append(actions);
    body.append(tr);
  }
  $('release-next').disabled = !questions.some((q) => !q.released);
  $('extend').disabled = !current;
}

// ---------- marking typed answers ----------

let reviewQuestionId = null;
let shownReviewOptions = '';

// Keep the typed-question list and the "still to mark" warning up to date (only touches them when they change).
function renderReviewPicker(questions) {
  const typed = questions.filter((q) => q.type === 'TYPED' && q.released);
  const label = (q) => `Q${q.orderIndex} · ${q.awaitingMarks ? `${q.awaitingMarks} to mark` : 'all marked'} · ${q.text.slice(0, 60)}`;
  const options = typed.map((q) => `${q.id}|${label(q)}`).join('\n');
  if (options !== shownReviewOptions) {
    shownReviewOptions = options;
    const select = $('review-question');
    const chosen = select.value;
    select.replaceChildren(new Option(typed.length ? 'Choose a typed question…' : 'No typed question released yet', ''));
    for (const q of typed) select.append(new Option(label(q), q.id));
    select.value = typed.some((q) => String(q.id) === chosen) ? chosen : '';
  }
  const waiting = questions.reduce((sum, q) => sum + q.awaitingMarks, 0);
  $('marks-warning').hidden = waiting === 0;
  $('marks-warning').textContent = `${waiting} typed answer${waiting === 1 ? '' : 's'} still to mark: they show as "review" in the sheet and are not in Total.`;
}

// Load one question's grouped answers. Not on the 1-second refresh: that would wipe a mark being typed.
async function loadReview() {
  reviewQuestionId = $('review-question').value || null;
  $('review-groups').replaceChildren();
  $('review-late').hidden = true;
  $('review-summary').textContent = '';
  if (!reviewQuestionId) return;
  const review = await api('GET', `/api/sessions/${sessionId}/questions/${reviewQuestionId}/review`);
  $('review-summary').textContent = `${review.marked} of ${review.marked + review.pending} marked · out of ${review.maxMarks}`;
  $('review-groups').replaceChildren(...review.groups.map((g) => reviewGroup(g, review.maxMarks)));
  if (!review.groups.length) {
    const empty = document.createElement('p');
    empty.className = 'muted';
    empty.textContent = 'No on-time answers to mark.';
    $('review-groups').append(empty);
  }
  $('review-late').hidden = review.late.length === 0;
  $('review-late-list').replaceChildren(...review.late.map((a) => {
    const li = document.createElement('li');
    li.textContent = showNames() ? `${a.rollNo}: ${a.text}` : a.text;
    return li;
  }));
}

// Are roll numbers shown? Off by default so the marker isn't influenced by whose answer it is.
function showNames() {
  return $('review-names').checked;
}

// A marks box with a Save button; saving reloads the list. An empty box clears the mark.
function markControl(current, maxMarks, label, save) {
  const row = document.createElement('div');
  row.className = 'mark-row';
  const input = document.createElement('input');
  input.type = 'number';
  input.min = '0';
  input.max = String(maxMarks);
  input.step = '0.5';
  input.value = current ?? '';
  input.setAttribute('aria-label', 'Marks');
  const button = document.createElement('button');
  button.textContent = label;
  button.addEventListener('click', () => run(async () => {
    await save(input.value === '' ? null : Number(input.value));
    await loadReview();
  }));
  const outOf = document.createElement('span');
  outOf.className = 'muted';
  outOf.textContent = `/ ${maxMarks}`;
  row.append(input, outOf, button);
  return row;
}

// One group of identical answers: the wording, how many said it, one marks box for them all.
function reviewGroup(group, maxMarks) {
  const box = document.createElement('div');
  box.className = group.marks !== null ? 'review-group marked' : 'review-group';
  const text = document.createElement('div');
  text.className = 'answer';
  text.textContent = group.sample;
  const save = (marks) => api('POST', `/api/sessions/${sessionId}/questions/${reviewQuestionId}/review/group`, { key: group.key, marks });
  const controls = markControl(group.marks, maxMarks, group.count > 1 ? `Save for all ${group.count}` : 'Save', save);
  if (group.mixed) controls.append(pill('marked differently below', 'warn'));
  box.append(text, controls);

  const details = document.createElement('details');
  const summary = document.createElement('summary');
  summary.className = 'muted';
  summary.textContent = group.count > 1 ? `${group.count} students: mark one differently` : 'Student answer';
  details.append(summary);
  group.answers.forEach((answer, index) => {
    const row = document.createElement('div');
    row.className = 'student-answer';
    const said = document.createElement('div');
    said.className = 'answer';
    said.textContent = `${showNames() ? answer.rollNo : `Student ${index + 1}`}: ${answer.text}`;
    row.append(said, markControl(answer.marks, maxMarks, 'Save', (marks) =>
      api('POST', `/api/sessions/${sessionId}/responses/${answer.responseId}/marks`, { marks })));
    details.append(row);
  });
  box.append(details);
  return box;
}

// ---------- results by question ----------

// One option's row: its text, a bar for its share of answers, and the figures.
function optionBar(option) {
  const row = document.createElement('div');
  row.className = 'option-bar';
  row.title = `${option.count} answer${option.count === 1 ? '' : 's'}, ${option.percent}%`;
  const name = document.createElement('span');
  name.className = 'name';
  name.textContent = option.correct ? `${option.text} (correct ✓)` : option.text;
  const track = document.createElement('div');
  track.className = 'track';
  const fill = document.createElement('div');
  fill.className = option.count ? 'fill' : 'fill empty';
  fill.style.width = `${option.percent}%`;
  track.append(fill);
  const figure = document.createElement('span');
  figure.className = 'figure';
  figure.textContent = `${option.count} · ${option.percent}%`;
  row.append(name, track, figure);
  return row;
}

// One question's result: headline figures, how the options split, and any warnings.
function resultCard(q) {
  const box = document.createElement('div');
  box.className = 'review-group';
  const head = document.createElement('div');
  head.className = 'result-head';
  const title = document.createElement('span');
  title.className = 'answer';
  title.textContent = `Q${q.orderIndex}. ${q.text}`;
  const figures = document.createElement('span');
  figures.className = 'result-figures';
  const percent = q.percentCorrect === null ? '—' : `${q.percentCorrect}%`;
  const discrimination = q.discrimination === null ? 'not enough data' : q.discrimination.toFixed(2);
  figures.append('correct ', Object.assign(document.createElement('b'), { textContent: percent }),
    ' · discrimination ', Object.assign(document.createElement('b'), { textContent: discrimination }),
    ` · ${q.answered} on time, ${q.late} late`);
  head.append(title, figures);
  box.append(head);
  if (q.type === 'MCQ') {
    const bars = document.createElement('div');
    bars.className = 'option-bars';
    bars.append(...q.options.map(optionBar));
    box.append(bars);
  } else if (q.awaitingMarks) {
    const waiting = document.createElement('p');
    waiting.className = 'muted';
    waiting.textContent = `${q.awaitingMarks} still to mark; the figures are from the marked answers only.`;
    box.append(waiting);
  }
  for (const note of q.notes) {
    const p = document.createElement('p');
    p.className = 'result-note';
    p.textContent = note;
    box.append(p);
  }
  if (q.type === 'MCQ') {
    const actions = document.createElement('div');
    actions.className = 'result-actions';
    const fix = document.createElement('button');
    fix.className = 'secondary';
    fix.textContent = 'Change correct answer';
    fix.addEventListener('click', () => changeAnswerKey(q));
    actions.append(fix);
    box.append(actions);
  }
  return box;
}

// Ask for the right option, re-score everyone, and refresh the results.
function changeAnswerKey(q) {
  const list = q.options.map((o, i) => `${i + 1}. ${o.text}`).join('\n');
  const answer = window.prompt(`Which option is correct for Q${q.orderIndex}? Every answer will be re-scored.\n${list}`);
  if (answer === null) return;
  run(async () => {
    const number = Number(answer);
    if (!Number.isInteger(number) || number < 1 || number > q.options.length) {
      throw new Error(`Enter an option number from 1 to ${q.options.length}.`);
    }
    await api('POST', `/api/sessions/${sessionId}/questions/${q.questionId}/answer-key?correctOptionIndex=${number - 1}`);
    await loadAnalytics();
    say(`Q${q.orderIndex} re-scored.`);
  });
}

// Load the results on request, so they aren't on the projector by accident during a question.
async function loadAnalytics() {
  const questions = await api('GET', `/api/sessions/${sessionId}/analytics`);
  $('analytics').replaceChildren(...questions.map(resultCard));
  if (!questions.length) {
    const empty = document.createElement('p');
    empty.className = 'muted';
    empty.textContent = 'No question released yet.';
    $('analytics').append(empty);
  }
}

// One line per session, so she can see how the class is doing across the semester.
async function loadSemester() {
  const sessions = await api('GET', '/api/analytics/semester');
  $('semester').replaceChildren(...sessions.map((s) => {
    const tr = document.createElement('tr');
    tr.append(cell(`#${s.sessionId} · ${s.title}`), cell(new Date(s.createdAt).toLocaleDateString()),
      cell(`${s.joined} / ${s.rosterSize}`), cell(s.released),
      cell(s.averagePercentCorrect === null ? '—' : `${s.averagePercentCorrect}%`),
      cell(s.hardestQuestionOrder === null ? '—' : `Q${s.hardestQuestionOrder} (${s.hardestQuestionPercent}%)`));
    return tr;
  }));
  $('semester-wrap').hidden = false;
}

// The attendance table and the rule that decided it.
async function renderAttendance() {
  const report = await api('GET', `/api/sessions/${sessionId}/attendance`);
  const body = $('attendance');
  body.replaceChildren();
  let present = 0;
  for (const row of report.rows) {
    if (row.status === 'PRESENT') present++;
    const tr = document.createElement('tr');
    const status = document.createElement('td');
    status.append(pill(row.status, row.status === 'PRESENT' ? 'ok' : 'bad'));
    const flags = document.createElement('td');
    for (const flag of row.flags) {
      flags.append(pill(flag, 'warn'), document.createTextNode(' '));
    }
    tr.append(cell(row.rollNo), status, cell(`${row.answered} / ${report.releasedQuestions}`), cell(row.joined ? 'yes' : 'no'), flags);
    body.append(tr);
  }
  const rule = report.basis === 'JOINED_ONLY'
    ? 'No question released yet, so present = joined.'
    : `Present = answered at least ${Math.round(report.threshold * 100)}% of ${report.releasedQuestions} released questions (late answers count).`;
  $('attendance-summary').textContent = `${present} of ${report.rows.length} present. ${rule}`;
}

// ---------- actions ----------

$('session-select').addEventListener('change', (e) => selectSession(e.target.value));

$('create-session').addEventListener('click', () => run(async () => {
  const title = $('new-title').value.trim() || `Lecture ${new Date().toLocaleDateString()}`;
  const created = await api('POST', '/api/sessions', { title });
  $('new-title').value = '';
  await loadSessions();
  $('session-select').value = created.id;
  selectSession(String(created.id));
  say(`Created session #${created.id}.`);
}));

$('upload-roster').addEventListener('click', () => run(async () => {
  const file = $('roster-file').files[0];
  if (!file) throw new Error('Choose a roster CSV first.');
  const form = new FormData();
  form.append('file', file);
  const result = await api('POST', `/api/roster/${sessionId}/upload`, form);
  say(`Roster uploaded: ${result.rosterSize} roll numbers.`);
}));

$('upload-questions').addEventListener('click', () => run(async () => {
  const file = $('questions-file').files[0];
  if (!file) throw new Error('Choose a questions CSV first.');
  const form = new FormData();
  form.append('file', file);
  const result = await api('POST', `/api/sessions/${sessionId}/questions/upload`, form);
  say(`Added ${result.added} questions.`);
}));

$('marks-visible').addEventListener('change', (e) => run(async () => {
  await api('POST', `/api/sessions/${sessionId}/marks-visibility?visible=${e.target.checked}`);
  say(e.target.checked
    ? 'Students can now see their own marks on closed questions. Answers still to mark show as "being marked".'
    : 'Marks hidden from students.');
}));

$('add-question').addEventListener('click', () => run(async () => {
  const type = $('q-type').value;
  const text = $('q-text').value.trim();
  if (!text) throw new Error('Write the question text first.');
  const options = type === 'MCQ' ? $('q-options').value.split('\n').map((o) => o.trim()).filter(Boolean) : [];
  const body = {
    orderIndex: (dashboard?.questions.length ?? 0) + 1,
    type,
    text,
    options,
    correctOptionIndex: type === 'MCQ' ? Number($('q-correct').value) - 1 : null,
    durationSeconds: Number($('q-duration').value),
    maxMarks: Number($('q-marks').value),
  };
  await api('POST', `/api/sessions/${sessionId}/questions`, body);
  $('q-text').value = '';
  $('q-options').value = '';
  say(`Added question ${body.orderIndex}.`);
}));

$('open-join').addEventListener('click', () => run(async () => {
  await api('POST', `/api/sessions/${sessionId}/join-window/open?seconds=${Number($('join-seconds').value)}`);
  say('Joining is open. Students scan the QR on the screen.');
}));

$('close-join').addEventListener('click', () => run(async () => {
  await api('POST', `/api/sessions/${sessionId}/join-window/close`);
  say('Joining closed.');
}));

$('release-next').addEventListener('click', () => run(async () => {
  const next = dashboard.questions.find((q) => !q.released);
  await api('POST', `/api/sessions/${sessionId}/questions/${next.id}/release`);
  say(`Released question ${next.orderIndex}.`);
}));

$('extend').addEventListener('click', () => run(async () => {
  const current = dashboard.questions.find((q) => q.remainingMs !== null);
  await api('POST', `/api/sessions/${sessionId}/questions/${current.id}/extend?seconds=30`);
  say(`Question ${current.orderIndex} extended by 30 s.`);
}));

$('refresh-attendance').addEventListener('click', () => renderAttendance().catch((e) => say(e.message, true)));

$('analytics-load').addEventListener('click', () => loadAnalytics().catch((e) => say(e.message, true)));
$('semester-load').addEventListener('click', () => loadSemester().catch((e) => say(e.message, true)));

$('review-question').addEventListener('change', () => loadReview().catch((e) => say(e.message, true)));
$('review-load').addEventListener('click', () => loadReview().catch((e) => say(e.message, true)));
$('review-names').addEventListener('change', () => loadReview().catch((e) => say(e.message, true)));

// Options and "correct option" only apply to multiple choice.
$('q-type').addEventListener('change', (e) => {
  const mcq = e.target.value === 'MCQ';
  $('q-options').hidden = !mcq;
  $('q-correct-label').hidden = !mcq;
});

$('doubts-enabled').addEventListener('change', (e) => run(async () => {
  await api('POST', `/api/sessions/${sessionId}/doubts-settings?enabled=${e.target.checked}`);
  say(e.target.checked ? 'Doubts channel on.' : 'Doubts channel off: students can no longer send doubts.');
}));

$('doubts-authors').addEventListener('change', (e) => run(async () => {
  await api('POST', `/api/sessions/${sessionId}/doubts-settings?authorsVisible=${e.target.checked}`);
}));

$('doubts-shared').addEventListener('change', (e) => run(async () => {
  await api('POST', `/api/sessions/${sessionId}/doubts-settings?shared=${e.target.checked}`);
  say(e.target.checked ? 'Students can see and upvote doubts.' : 'Doubts now go only to you; students no longer see them.');
}));

$('base-url').addEventListener('change', () => { shownQrKey = null; refresh(); });

// ---------- start ----------

// List this laptop's network addresses so she can pick the classroom Wi-Fi one for the QR.
async function loadAddresses() {
  const addresses = await api('GET', '/api/dashboard/host-addresses');
  const select = $('base-url');
  select.replaceChildren();
  for (const a of addresses) select.append(new Option(`${a.url} (${a.adapter})`, a.url));
  if (!addresses.length) {
    select.append(new Option('No network found: connect this laptop to the classroom Wi-Fi', ''));
  }
}

loadAddresses().catch((e) => say(e.message, true));
loadSessions().catch((e) => say(e.message, true));
setInterval(refresh, POLL_MS);
