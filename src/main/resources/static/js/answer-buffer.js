// Keeps every answer in localStorage until the server confirms it, so it survives a dropped
// connection, a page reload or a locked phone, and is resent with the SAME submissionId each time
// (that is how the server recognises a resend as a DUPLICATE instead of a second answer).

// A random ID that works on plain http (crypto.randomUUID needs https).
export function randomId() {
  // crypto.randomUUID() only works on HTTPS or localhost; the classroom uses plain http:// on the LAN.
  // getRandomValues() works everywhere.
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

export class AnswerBuffer {
  constructor(sessionId, rollNo) {
    this.storageKey = `quiz:${sessionId}:${rollNo}:answers`;
    this.entries = this.#read();
  }

  // Records the student's answer to a question. First answer is final, so a second call for the
  // same question returns the existing entry instead of creating a new one.
  add(answer) {
    const existing = this.entries[answer.questionId];
    if (existing) return existing;
    const entry = { submission: { ...answer, submissionId: randomId() }, status: 'PENDING', ack: null };
    this.entries[answer.questionId] = entry;
    this.#write();
    return entry;
  }

  // Choice questions: the newest tap replaces the older one, with a new submissionId and a bigger seq,
  // so the server keeps the latest. The older one is no longer resent. seq only orders this phone's
  // own taps (any clock is fine for that); whether it was on time is decided by the server.
  choose(answer) {
    const previous = this.entries[answer.questionId];
    const seq = Math.max(Date.now(), (previous?.submission.seq ?? 0) + 1);
    const entry = { submission: { ...answer, submissionId: randomId(), seq }, status: 'PENDING', ack: null };
    this.entries[answer.questionId] = entry;
    this.#write();
    return entry;
  }

  get(questionId) {
    return this.entries[questionId] ?? null;
  }

  pendingSubmissions() {
    return Object.values(this.entries)
      .filter((entry) => entry.status === 'PENDING')
      .map((entry) => entry.submission);
  }

  // Any server outcome (ACCEPTED, DUPLICATE, REJECTED) is final: stop resending.
  resolve(ack) {
    const entry = Object.values(this.entries).find((e) => e.submission.submissionId === ack.submissionId);
    if (!entry) return null;
    entry.status = ack.outcome;
    entry.ack = ack;
    this.#write();
    return entry;
  }

  #read() {
    try {
      return JSON.parse(localStorage.getItem(this.storageKey)) ?? {};
    } catch {
      return {};
    }
  }

  #write() {
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(this.entries));
    } catch {
      // Storage blocked (e.g. private mode): the in-memory copy still covers drops while the page stays open.
    }
  }
}
