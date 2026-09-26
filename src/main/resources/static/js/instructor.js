// Instructor dashboard: every action calls the owning module's endpoint; this page only displays.
const $ = (id) => document.getElementById(id);
const POLL_MS = 1000;

let sessionId = null;
let dashboard = null;
let shownQrKey = null;

// ---------- helpers ----------

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

function say(message, isError = false) {
  $('status').textContent = message;
  $('status').className = isError ? 'error' : '';
}

async function run(action) {
  try {
    await action();
    await refresh();
  } catch (e) {
    say(e.message, true);
  }
}

function cell(text) {
  const td = document.createElement('td');
  td.textContent = text;
  return td;
}

function pill(text, kind) {
  const span = document.createElement('span');
  span.className = `pill ${kind}`;
  span.textContent = text;
  return span;
}

// ---------- sessions ----------

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

function selectSession(id) {
  sessionId = id || null;
  shownQrKey = null;
  $('panels').hidden = !sessionId;
  if (sessionId) localStorage.setItem('quiz:instructor-session', sessionId);
  $('attendance').replaceChildren();
  $('attendance-summary').textContent = '';
  refresh();
}

// ---------- rendering ----------

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
}

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

function renderQuestions(questions) {
  const body = $('questions');
  body.replaceChildren();
  const current = questions.find((q) => q.remainingMs !== null);
  for (const q of questions) {
    const tr = document.createElement('tr');
    tr.append(cell(q.orderIndex), cell(q.text));
    const state = document.createElement('td');
    if (q === current) {
      state.append(q.remainingMs > 0 ? pill(`open · ${Math.ceil(q.remainingMs / 1000)}s left`, 'ok') : pill('time up', 'warn'));
    } else {
      state.append(q.released ? pill('done', '') : pill('not released', ''));
    }
    tr.append(state, cell(q.released ? `${q.answered} / ${dashboard.joined}` : '—'));
    body.append(tr);
  }
  $('release-next').disabled = !questions.some((q) => !q.released);
  $('extend').disabled = !current;
}

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

$('base-url').addEventListener('change', () => { shownQrKey = null; refresh(); });

// ---------- start ----------

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
