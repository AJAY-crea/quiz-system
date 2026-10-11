// Minimal STOMP 1.2 client over a plain WebSocket. Bundled here instead of loading a library from
// a CDN because the classroom network may have no internet access.

// Tiny STOMP client over a plain WebSocket: connect, subscribe to channels, send JSON.
export function connectStomp(url, { onConnect, onClose }) {
  const socket = new WebSocket(url);
  const handlers = new Map();
  let nextSubscriptionId = 0;
  let received = '';

  // Write one STOMP frame: command line, headers, blank line, body, NUL terminator.
  function sendFrame(command, headers, body = '') {
    let frame = command + '\n';
    for (const [name, value] of Object.entries(headers)) {
      frame += `${name}:${value}\n`;
    }
    socket.send(frame + '\n' + body + '\0');
  }

  const client = {
    subscribe(destination, onMessage) {
      const id = 'sub-' + nextSubscriptionId++;
      handlers.set(id, onMessage);
      sendFrame('SUBSCRIBE', { id, destination });
    },
    send(destination, payload) {
      sendFrame('SEND', { destination, 'content-type': 'application/json' }, JSON.stringify(payload));
    },
    close() {
      socket.close();
    },
  };

  // Read one STOMP frame from the server and pass messages to the right subscriber.
  function handleFrame(raw) {
    const headerEnd = raw.indexOf('\n\n');
    const headerLines = raw.slice(0, headerEnd === -1 ? raw.length : headerEnd).split('\n');
    const body = headerEnd === -1 ? '' : raw.slice(headerEnd + 2);
    const command = headerLines[0].trim();
    const headers = {};
    for (const line of headerLines.slice(1)) {
      const colon = line.indexOf(':');
      if (colon > 0) headers[line.slice(0, colon)] = line.slice(colon + 1);
    }

    if (command === 'CONNECTED') {
      onConnect(client);
    } else if (command === 'MESSAGE') {
      const handler = handlers.get(headers.subscription);
      if (handler) handler(JSON.parse(body));
    } else if (command === 'ERROR') {
      console.error('STOMP error from server:', headers.message, body);
    }
  }

  socket.onopen = () => sendFrame('CONNECT', { 'accept-version': '1.2', 'heart-beat': '0,0', host: 'quiz' });
  socket.onmessage = (event) => {
    // A single WebSocket message can carry several frames, or part of one; frames end with NUL.
    received += event.data;
    let end;
    while ((end = received.indexOf('\0')) !== -1) {
      const raw = received.slice(0, end).replace(/^[\r\n]+/, '');
      received = received.slice(end + 1);
      if (raw) handleFrame(raw);
    }
  };
  socket.onclose = () => onClose();

  return client;
}
