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

function deviceToken() {
  let token = safeGet('quiz:device');
  if (!token) {
    token = randomId();
    safeSet('quiz:device', token);
  }
  return token;
}

function safeGet(key) {
  try { return localStorage.getItem(key); } catch { return null; }
}

function safeSet(key, value) {
  try { localStorage.setItem(key, value); } catch { /* ignore */ }
}

async function join(event) {
  event.preventDefault();
  const sessionId = $('session-id').value.trim();
  const rollNo = $('roll-no').value.trim().toUpperCase();
  const token = deviceToken();
  $('join-error').textContent = '';

  const response = await fetch(`/api/roster/${encodeURIComponent(sessionId)}/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ rollNo, deviceToken: token }),
  }).catch(() => null);

  if (!response) {
    $('join-error').textContent = 'Cannot reach the quiz server. Check you are on the classroom network.';
    return;
  }
  if (response.status === 403) {
    $('join-error').textContent = `${rollNo} is not on this class's roster.`;
    return;
  }
  if (!response.ok) {
    $('join-error').textContent = 'Could not join. Check the session number.';
    return;
  }
  identity = { sessionId, rollNo, deviceToken: token };
  safeSet('quiz:identity', JSON.stringify(identity));
  startQuiz();
}

// ---------- connection + resend ----------

function startQuiz() {
  buffer = new AnswerBuffer(identity.sessionId, identity.rollNo);
  $('join').hidden = true;
  $('quiz').hidden = false;
  $('who').textContent = `${identity.rollNo} · session ${identity.sessionId}`;
  connect();
  setInterval(resendPending, RESEND_INTERVAL_MS);
  refreshState();
}

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

function resendPending() {
  if (!connected) return;
  for (const submission of buffer.pendingSubmissions()) {
    stomp.send(`/app/session/${identity.sessionId}/answer`, submission);
  }
}

function onAck(ack) {
  const entry = buffer.resolve(ack);
  if (entry && currentQuestion && entry.submission.questionId === currentQuestion.id) {
    showAnswerStatus(entry);
  }
}

function setConnection(text, ok) {
  $('connection').textContent = text;
  $('connection').className = ok ? 'pill ok' : 'pill warn';
}

// ---------- rendering ----------

async function refreshState() {
  const { sessionId, rollNo, deviceToken: token } = identity;
  const url = `/api/sessions/${encodeURIComponent(sessionId)}/state`
    + `?rollNo=${encodeURIComponent(rollNo)}&deviceToken=${encodeURIComponent(token)}`;
  const response = await fetch(url).catch(() => null);
  if (!response) return; // offline; the reconnect path will call us again
  if (response.status === 403) {
    showMessage('This roll number has been joined from another device. Ask the instructor.');
    return;
  }
  if (!response.ok) return;
  render(await response.json());
}

function render(state) {
  currentQuestion = state.currentQuestion;
  clearInterval(countdownTimer);

  if (!currentQuestion) {
    showMessage('Waiting for the instructor to release the first question…');
    return;
  }

  $('message').hidden = true;
  $('question').hidden = false;
  $('question-number').textContent = `Question ${currentQuestion.orderIndex}`;
  $('question-text').textContent = currentQuestion.text;
  startCountdown(state.remainingMs);

  const entry = buffer.get(currentQuestion.id);
  if (entry || state.alreadyAnswered) {
    $('answer-form').hidden = true;
    showAnswerStatus(entry);
    return;
  }
  renderAnswerForm();
}

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

function startCountdown(remainingMs) {
  // Display only: the server alone decides whether an answer is on time.
  const endsAt = performance.now() + remainingMs;
  const tick = () => {
    const secondsLeft = Math.max(0, Math.ceil((endsAt - performance.now()) / 1000));
    $('countdown').textContent = secondsLeft > 0 ? `${secondsLeft}s left` : 'Time is up';
  };
  tick();
  countdownTimer = setInterval(tick, 250);
}

function showMessage(text) {
  $('question').hidden = true;
  $('message').hidden = false;
  $('message').textContent = text;
}

// ---------- start ----------

$('join-form').addEventListener('submit', join);
$('answer-form').addEventListener('submit', submitAnswer);

const saved = safeGet('quiz:identity');
if (saved) {
  try {
    identity = JSON.parse(saved);
  } catch {
    identity = null;
  }
}
if (identity) startQuiz();
