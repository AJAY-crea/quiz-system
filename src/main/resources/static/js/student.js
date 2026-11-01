import { connectStomp } from './stomp-lite.js';
import { AnswerBuffer, randomId } from './answer-buffer.js';

const RESEND_INTERVAL_MS = 5000;
const MAX_RECONNECT_DELAY_MS = 10000;

const $ = (id) => document.getElementById(id);

let identity = null;       // { sessionId, rollNo, deviceToken }
let buffer = null;
let stomp = null;
let connected = false;
let reconnectDelayMs = 1000;
let currentQuestion = null;
let countdownTimer = null;

// ---------- identity ----------

// This phone's random ID, created once and kept in browser storage (used to spot one phone joining as two students).
function deviceToken() {
  let token = safeGet('quiz:device');
  if (!token) {
    token = randomId();
    safeSet('quiz:device', token);
  }
  return token;
}

// Read from browser storage without crashing if storage is blocked (e.g. private mode).
function safeGet(key) {
  try { return localStorage.getItem(key); } catch { return null; }
}

// Write to browser storage without crashing if storage is blocked.
function safeSet(key, value) {
  try { localStorage.setItem(key, value); } catch { /* ignore */ }
}

// Scanning proves you were in the room while joining was open: the QR's code is swapped straight
// away for a ticket that gives you 2 minutes to type your roll number.
let pendingJoin = null; // { sessionId, ticket }

// Swap the scanned QR code for a 2-minute join ticket.
async function fetchTicket(sessionId, code) {
  const response = await fetch(
    `/api/sessions/${encodeURIComponent(sessionId)}/join-ticket?code=${encodeURIComponent(code)}`,
    { method: 'POST' },
  ).catch(() => null);
  if (!response) throw new Error('Cannot reach the quiz server. Check you are on the classroom Wi-Fi.');
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.error ?? 'Could not start joining. Check the session number.');
  return body.ticket;
}

// Join button: get a ticket if needed, then join with roll number + device ID; show clear errors.
async function join(event) {
  event.preventDefault();
  const rollNo = $('roll-no').value.trim().toUpperCase();
  const token = deviceToken();
  $('join-error').textContent = '';

  try {
    if (!pendingJoin) {
      const sessionId = $('session-id').value.trim();
      const code = $('join-code').value.trim();
      if (!sessionId || !code) throw new Error('Enter the session number and join code shown on the screen.');
      pendingJoin = { sessionId, ticket: await fetchTicket(sessionId, code) };
    }
  } catch (e) {
    $('join-error').textContent = e.message;
    return;
  }

  const { sessionId, ticket } = pendingJoin;
  const response = await fetch(`/api/roster/${encodeURIComponent(sessionId)}/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ rollNo, deviceToken: token, ticket }),
  }).catch(() => null);

  if (!response) {
    $('join-error').textContent = 'Cannot reach the quiz server. Check you are on the classroom Wi-Fi.';
    return;
  }
  const result = await response.json().catch(() => ({}));
  if (result.outcome === 'REJECTED_UNLISTED') {
    $('join-error').textContent = `${rollNo} is not on this class's roster.`;
    return;
  }
  if (result.outcome === 'REJECTED_INVALID_TICKET') {
    pendingJoin = null;
    showManualFields(true);
    $('join-error').textContent = 'Your scan has expired. Scan the QR code on the screen again.';
    return;
  }
  if (!response.ok) {
    $('join-error').textContent = result.error ?? 'Could not join. Ask the instructor.';
    return;
  }
  identity = { sessionId, rollNo, deviceToken: token };
  safeSet('quiz:identity', JSON.stringify(identity));
  safeSet('quiz:rollNo', rollNo);
  history.replaceState(null, '', location.pathname); // don't reuse a stale QR code on reload
  startQuiz();
}

// Show or hide the "type session number and code" fields (hidden when the QR was scanned).
function showManualFields(show) {
  $('manual-fields').hidden = !show;
}

// Page opened from the QR: get the ticket straight away so slow typing does not matter.
async function startFromQr(sessionId, code) {
  showManualFields(false);
  $('join-button').disabled = true;
  try {
    pendingJoin = { sessionId, ticket: await fetchTicket(sessionId, code) };
  } catch (e) {
    showManualFields(true);
    $('session-id').value = sessionId;
    $('join-error').textContent = e.message;
  } finally {
    $('join-button').disabled = false;
  }
}

// ---------- connection + resend ----------

// After joining: show the quiz screen, connect live, start the resend timer, load the current state.
function startQuiz() {
  buffer = new AnswerBuffer(identity.sessionId, identity.rollNo);
  $('join').hidden = true;
  $('quiz').hidden = false;
  $('who').textContent = `${identity.rollNo} · session ${identity.sessionId}`;
  connect();
  setInterval(resendPending, RESEND_INTERVAL_MS);
  setInterval(flushSignals, RESEND_INTERVAL_MS); // unsent integrity signals, e.g. after being offline
  reportReturn(); // the page was hidden or closed during a question last time
  flushSignals();
  refreshState();
}

// Open the live connection; on loss, reconnect with growing delays (1 s, 2 s, 4 s... max 10 s).
function connect() {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  setConnection('Connecting…', false);
  stomp = connectStomp(`${scheme}://${location.host}/ws/websocket`, {
    onConnect: (client) => {
      connected = true;
      reconnectDelayMs = 1000;
      setConnection('Connected', true);
      client.subscribe(`/topic/session/${identity.sessionId}/question`, () => refreshState());
      client.subscribe('/user/queue/answer-ack', onAck);
      resendPending();
      // Anything broadcast while we were disconnected (or while the server restarted) was missed:
      // ask the server for the current question and time left instead of trusting our own timer.
      refreshState();
    },
    onClose: () => {
      connected = false;
      setConnection('Connection lost — reconnecting…', false);
      setTimeout(connect, reconnectDelayMs);
      reconnectDelayMs = Math.min(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS);
    },
  });
}

// Send every answer the server has not confirmed yet (same submissionId each time).
function resendPending() {
  if (!connected) return;
  for (const submission of buffer.pendingSubmissions()) {
    stomp.send(`/app/session/${identity.sessionId}/answer`, submission);
  }
}

// Server confirmed an answer: stop resending it and show the result.
function onAck(ack) {
  const entry = buffer.resolve(ack);
  if (entry && currentQuestion && entry.submission.questionId === currentQuestion.id) {
    showAnswerStatus(entry);
  }
}

// Update the Connected / Reconnecting badge.
function setConnection(text, ok) {
  $('connection').textContent = text;
  $('connection').className = ok ? 'pill ok' : 'pill warn';
}

// ---------- rendering ----------

// Ask the server what to show now (question, my option order, time left). Called after every reconnect/broadcast.
async function refreshState() {
  const { sessionId, rollNo, deviceToken: token } = identity;
  const url = `/api/sessions/${encodeURIComponent(sessionId)}/state`
    + `?rollNo=${encodeURIComponent(rollNo)}&deviceToken=${encodeURIComponent(token)}`;
  const response = await fetch(url).catch(() => null);
  if (!response) return; // offline; the reconnect path will call us again
  if (response.status === 403) {
    const body = await response.json().catch(() => ({}));
    if ((body.error ?? '').includes('has not joined')) {
      // The saved identity belongs to an older session with the same number (e.g. the server's
      // database was reset), so this session has never seen this phone: go back to the join form.
      forgetIdentityAndRejoin(identity.sessionId);
      return;
    }
    showMessage('This roll number has been joined from another device. Ask the instructor.');
    return;
  }
  if (!response.ok) return;
  render(await response.json());
  refreshMarks();
}

// Show this student's marks if the instructor has published them.
async function refreshMarks() {
  const { sessionId, rollNo, deviceToken: token } = identity;
  const url = `/api/sessions/${encodeURIComponent(sessionId)}/my-marks`
    + `?rollNo=${encodeURIComponent(rollNo)}&deviceToken=${encodeURIComponent(token)}`;
  const response = await fetch(url).catch(() => null);
  if (!response || !response.ok) return;
  const marks = await response.json();
  $('my-marks').hidden = !marks.published;
  if (!marks.published) return;
  const words = { AWAITING_MARKS: 'being marked', NOT_ANSWERED: 'not answered', LATE: 'late' };
  $('my-marks-list').replaceChildren(...marks.rows.map((row) => {
    const li = document.createElement('li');
    const name = document.createElement('span');
    name.textContent = `Question ${row.orderIndex}`;
    const value = document.createElement('span');
    const word = words[row.status];
    value.textContent = (row.status === 'AWAITING_MARKS' ? `— / ${row.maxMarks}` : `${row.marks ?? 0} / ${row.maxMarks}`) + (word ? ` (${word})` : '');
    li.append(name, value);
    return li;
  }));
  const awaiting = marks.rows.some((row) => row.status === 'AWAITING_MARKS');
  $('my-marks-total').textContent = `Total: ${marks.total} / ${marks.outOf}${awaiting ? ' so far' : ''}`;
}

// Drop the saved identity and reload into the join form for this session (roll number stays pre-filled).
function forgetIdentityAndRejoin(sessionId) {
  try {
    localStorage.removeItem('quiz:identity');
  } catch {
    // Storage blocked: the reload below still shows the join form because identity is not re-read.
  }
  // Opened from a fresh QR scan: reuse its code so the student just enters the roll number.
  const query = scannedSession === String(sessionId) && scannedCode
    ? `?s=${encodeURIComponent(scannedSession)}&c=${encodeURIComponent(scannedCode)}`
    : `?rejoin=${encodeURIComponent(sessionId)}`;
  location.replace(location.pathname + query);
}

// Draw the current question, the timer, and either the answer form or the answer status.
function render(state) {
  currentQuestion = state.currentQuestion;
  clearInterval(countdownTimer);

  if (!currentQuestion) {
    letScreenSleep();
    showMessage('Waiting for the instructor to release the first question…');
    return;
  }

  $('message').hidden = true;
  $('question').hidden = false;
  $('question-number').textContent = `Question ${currentQuestion.orderIndex}`;
  $('question-text').textContent = currentQuestion.text;
  startCountdown(state.remainingMs);
  keepScreenAwake();

  const entry = buffer.get(currentQuestion.id);
  if (entry || state.alreadyAnswered) {
    $('answer-form').hidden = true;
    showAnswerStatus(entry);
    return;
  }
  renderAnswerForm();
}

// Draw the options (in this student's order) or the typed-answer box.
function renderAnswerForm() {
  const form = $('answer-form');
  const options = $('options');
  options.replaceChildren();
  form.hidden = false;
  $('answer-status').textContent = '';

  if (currentQuestion.type === 'MCQ') {
    $('typed').hidden = true;
    currentQuestion.options.forEach((text, index) => {
      const label = document.createElement('label');
      label.className = 'option';
      const radio = document.createElement('input');
      radio.type = 'radio';
      radio.name = 'choice';
      radio.value = String(index);
      label.append(radio, document.createTextNode(' ' + text));
      options.append(label);
    });
  } else {
    $('typed').hidden = false;
    $('typed').value = '';
  }
}

// Submit: store the answer on the phone first, then send it.
function submitAnswer(event) {
  event.preventDefault();
  const answer = { rollNo: identity.rollNo, deviceToken: identity.deviceToken, questionId: currentQuestion.id };

  if (currentQuestion.type === 'MCQ') {
    const chosen = document.querySelector('input[name="choice"]:checked');
    if (!chosen) return;
    answer.chosenIndex = Number(chosen.value);
  } else {
    const typed = $('typed').value.trim();
    if (!typed) return;
    answer.typedAnswer = typed;
  }

  const entry = buffer.add(answer);
  $('answer-form').hidden = true;
  showAnswerStatus(entry);
  resendPending();
}

// Turn the server's reply into a message: saved / late / first answer stands / not accepted.
function showAnswerStatus(entry) {
  const status = $('answer-status');
  if (!entry || entry.status === 'PENDING') {
    status.textContent = entry ? 'Sending… (kept on this phone until the server confirms)' : 'Answer recorded.';
    return;
  }
  const ack = entry.ack;
  if (ack.outcome === 'ACCEPTED') {
    status.textContent = ack.onTime ? 'Answer saved ✓' : 'Saved, but after the deadline — it is marked late.';
  } else if (ack.outcome === 'DUPLICATE') {
    status.textContent = ack.sameSubmission ? 'Answer saved ✓' : 'You had already answered this question; your first answer stands.';
  } else {
    status.textContent = `Not accepted: ${ack.reason}`;
  }
}

// Display-only timer from the server's "time left"; the server alone decides on-time.
function startCountdown(remainingMs) {
  // Display only: the server alone decides whether an answer is on time.
  const endsAt = performance.now() + remainingMs;
  questionClosesAt = Date.now() + remainingMs;
  const tick = () => {
    const secondsLeft = Math.max(0, Math.ceil((endsAt - performance.now()) / 1000));
    $('countdown').textContent = secondsLeft > 0 ? `${secondsLeft}s left` : 'Time is up';
    if (secondsLeft === 0) letScreenSleep();
  };
  tick();
  countdownTimer = setInterval(tick, 250);
}

// Show a message instead of a question (e.g. waiting for the instructor).
function showMessage(text) {
  $('question').hidden = true;
  $('message').hidden = false;
  $('message').textContent = text;
}

// ---------- integrity signals (advisory) ----------
// Recorded while a question is open, answered or not: students should stay on the quiz page
// until time is up. A web page cannot tell "switched to another app" from "screen turned off"
// (both just hide the page), so instead of a bare count we report HOW LONG the page was hidden,
// and keep the screen awake while a question is open where the browser allows it (Wake Lock,
// HTTPS only). A plain "blur" (notification shade, a system pop-up) is ignored: the quiz never
// left the screen. Signals are kept on the phone until the server has them, like answers, so
// going offline loses nothing.

const HIDDEN_KEY = 'quiz:hidden'; // { questionId, at } saved while the page is hidden
let questionClosesAt = 0;         // phone time when the open question's countdown ends (used only to gate signals)
let enteredFullscreen = false;
let wakeLock = null;
let flushingSignals = false;

// Signals are recorded while a question is open (answered or not).
function questionIsOpen() {
  return Boolean(identity && currentQuestion && Date.now() < questionClosesAt);
}

// Where this student's unsent signals are kept on the phone.
function signalsKey() {
  return `quiz:${identity.sessionId}:${identity.rollNo}:signals`;
}

// Unsent signals stored on the phone.
function readSignals() {
  try {
    return JSON.parse(safeGet(signalsKey())) ?? [];
  } catch {
    return [];
  }
}

// Keep a signal on the phone first, then try to send it.
function queueSignal(eventType, questionId, awayMs = null) {
  const pending = readSignals();
  pending.push({ rollNo: identity.rollNo, deviceToken: identity.deviceToken, questionId, eventType, awayMs });
  safeSet(signalsKey(), JSON.stringify(pending));
  flushSignals();
}

// Send every stored signal; keep the ones that couldn't reach the server (offline) for next time.
async function flushSignals() {
  if (!identity || flushingSignals) return;
  flushingSignals = true;
  try {
    const url = `/api/sessions/${encodeURIComponent(identity.sessionId)}/integrity`;
    const batch = readSignals();
    const unsent = [];
    for (const signal of batch) {
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(signal),
        keepalive: true,
      }).catch(() => null);
      if (!response) unsent.push(signal); // no network: any reply (even a refusal) means it's done
    }
    // Signals queued while we were sending were appended after the batch; keep them too.
    safeSet(signalsKey(), JSON.stringify(unsent.concat(readSignals().slice(batch.length))));
  } finally {
    flushingSignals = false;
  }
}

// The page is visible again (or was reopened): report how long it was hidden.
function reportReturn() {
  const saved = safeGet(HIDDEN_KEY);
  if (!saved || !identity) return;
  try {
    localStorage.removeItem(HIDDEN_KEY);
  } catch {
    // Storage blocked: nothing was saved either, so there is nothing to report.
  }
  try {
    const hidden = JSON.parse(saved);
    queueSignal('FOCUS_LOSS', hidden.questionId, Math.max(0, Date.now() - hidden.at));
  } catch {
    // Unreadable entry: drop it.
  }
}

// Ask the browser to keep the screen on while a question is open (needs HTTPS; skipped on plain http).
async function keepScreenAwake() {
  if (!('wakeLock' in navigator) || !questionIsOpen() || wakeLock) return;
  try {
    wakeLock = await navigator.wakeLock.request('screen');
    wakeLock.addEventListener('release', () => { wakeLock = null; });
  } catch {
    wakeLock = null;
  }
}

// Question over: let the screen sleep normally again.
function letScreenSleep() {
  wakeLock?.release().catch(() => {});
  wakeLock = null;
}

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') {
    // Remember when the page left the screen; the time away is reported on return.
    if (questionIsOpen() && !safeGet(HIDDEN_KEY)) {
      safeSet(HIDDEN_KEY, JSON.stringify({ questionId: currentQuestion.id, at: Date.now() }));
    }
  } else {
    reportReturn();
    keepScreenAwake(); // browsers drop the wake lock whenever the page is hidden
  }
});
$('typed').addEventListener('paste', () => {
  if (questionIsOpen()) queueSignal('PASTE', currentQuestion.id);
});

$('fullscreen').addEventListener('click', () => {
  document.documentElement.requestFullscreen?.().then(() => { enteredFullscreen = true; }).catch(() => {});
});
document.addEventListener('fullscreenchange', () => {
  if (enteredFullscreen && !document.fullscreenElement) {
    enteredFullscreen = false;
    if (questionIsOpen()) queueSignal('FULLSCREEN_EXIT', currentQuestion.id);
  }
});
// iPhones don't support fullscreen for web pages; hide the button where it can't work.
if (!document.documentElement.requestFullscreen) $('fullscreen').hidden = true;

// ---------- doubts ----------

$('doubt-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const text = $('doubt-text').value.trim();
  if (!text || !identity) return;
  $('doubt-status').textContent = 'Sending…';
  const response = await fetch(`/api/sessions/${encodeURIComponent(identity.sessionId)}/doubts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ rollNo: identity.rollNo, deviceToken: identity.deviceToken, text }),
  }).catch(() => null);
  if (!response) {
    $('doubt-status').textContent = 'Not sent: no connection. Your text is still here; try again.';
    return;
  }
  if (response.ok) {
    $('doubt-text').value = '';
    $('doubt-status').textContent = 'Sent to the instructor ✓';
  } else {
    const body = await response.json().catch(() => ({}));
    $('doubt-status').textContent = body.error ?? 'Could not send your doubt.';
  }
});

// ---------- start ----------

$('join-form').addEventListener('submit', join);
$('answer-form').addEventListener('submit', submitAnswer);

const params = new URLSearchParams(location.search);
const scannedSession = params.get('s');
const scannedCode = params.get('c');
const rejoinSession = params.get('rejoin');

const saved = safeGet('quiz:identity');
if (saved) {
  try {
    identity = JSON.parse(saved);
  } catch {
    identity = null;
  }
}
// A QR for a different (e.g. this week's) session means a new join, not resuming the old one.
if (identity && scannedSession && identity.sessionId !== scannedSession) identity = null;

$('roll-no').value = safeGet('quiz:rollNo') ?? '';
if (identity) {
  startQuiz();
} else if (scannedSession && scannedCode) {
  startFromQr(scannedSession, scannedCode);
} else if (rejoinSession) {
  $('session-id').value = rejoinSession;
  $('join-error').textContent =
    'This session does not know this phone yet (it may have been restarted). Scan the QR or enter the code on the screen to join again.';
}
