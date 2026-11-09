/*
 * Client side of the Voice Engine add-on: a thin layer on top of the Web Speech
 * API (speechSynthesis and SpeechRecognition).
 *
 * This is a plain ES module served by the Java side (SpeechClient) through a
 * DownloadHandler on the UI element. The Java side loads it
 * lazily with a dynamic import() from Element.executeJs, so the add-on needs no
 * frontend build (no @JsModule, no custom development bundle). The browser
 * caches the module, so repeated imports are cheap, and after a page reload
 * (e.g. with @PreserveOnRefresh) it is simply imported again.
 *
 * Events are dispatched as CustomEvents on the host element passed in by the
 * Java side (the UI element, i.e. <body>):
 *   voice-engine-synthesis   detail: { id, type: start|end|error|pause|resume, error? }
 *   voice-engine-recognition detail: { id, type: start|result|error|end, ... }
 */

const SpeechRecognitionImpl = window.SpeechRecognition || window.webkitSpeechRecognition;
const synth = window.speechSynthesis;

function fire(host, name, detail) {
  host.dispatchEvent(new CustomEvent(name, { detail }));
}

export function support() {
  return {
    synthesis: !!synth && typeof SpeechSynthesisUtterance !== 'undefined',
    recognition: !!SpeechRecognitionImpl,
  };
}

/* ---------------------------------------------------------------- synthesis */

function toVoice(v) {
  return { name: v.name, lang: v.lang, localService: v.localService, defaultVoice: v.default, voiceUri: v.voiceURI };
}

/**
 * Resolves the available voices. Some browsers (Chrome) load voices
 * asynchronously and report an empty list until "voiceschanged" fires.
 */
export function voices() {
  if (!synth) {
    return Promise.resolve([]);
  }
  const current = synth.getVoices();
  if (current.length > 0) {
    return Promise.resolve(current.map(toVoice));
  }
  return new Promise((resolve) => {
    const done = () => {
      synth.removeEventListener('voiceschanged', done);
      clearTimeout(timer);
      resolve(synth.getVoices().map(toVoice));
    };
    const timer = setTimeout(done, 3000);
    synth.addEventListener('voiceschanged', done);
  });
}

// Chrome stops long utterances after ~15 seconds unless poked.
let keepAliveTimer = null;
function keepAlive(on) {
  clearInterval(keepAliveTimer);
  keepAliveTimer = null;
  if (on) {
    keepAliveTimer = setInterval(() => {
      if (!synth.speaking) {
        keepAlive(false);
      } else if (!synth.paused) {
        synth.pause();
        synth.resume();
      }
    }, 10000);
  }
}

/**
 * Queues an utterance. Options: lang, voiceUri, voiceName, rate, pitch, volume.
 */
export async function speak(host, id, text, options) {
  const name = 'voice-engine-synthesis';
  if (!synth) {
    fire(host, name, { id, type: 'error', error: 'synthesis-unavailable' });
    return;
  }
  const opts = options || {};
  const utterance = new SpeechSynthesisUtterance(text);
  if (opts.lang) utterance.lang = opts.lang;
  if (opts.rate != null) utterance.rate = opts.rate;
  if (opts.pitch != null) utterance.pitch = opts.pitch;
  if (opts.volume != null) utterance.volume = opts.volume;
  if (opts.voiceUri || opts.voiceName) {
    await voices();
    const all = synth.getVoices();
    const voice = all.find((v) => opts.voiceUri && v.voiceURI === opts.voiceUri)
      || all.find((v) => opts.voiceName && v.name === opts.voiceName);
    if (voice) {
      utterance.voice = voice;
      if (!opts.lang) utterance.lang = voice.lang;
    }
  }
  utterance.onstart = () => { keepAlive(true); fire(host, name, { id, type: 'start' }); };
  utterance.onpause = () => fire(host, name, { id, type: 'pause' });
  utterance.onresume = () => fire(host, name, { id, type: 'resume' });
  utterance.onend = () => { keepAlive(synth.speaking); fire(host, name, { id, type: 'end' }); };
  utterance.onerror = (e) => { keepAlive(synth.speaking); fire(host, name, { id, type: 'error', error: e.error }); };
  synth.speak(utterance);
}

export function cancel() { if (synth) { keepAlive(false); synth.cancel(); } }
export function pause() { if (synth) synth.pause(); }
export function resume() { if (synth) synth.resume(); }

/* -------------------------------------------------------------- recognition */

const sessions = new Map();

/**
 * Starts a recognition session. Options: lang, continuous, interimResults,
 * maxAlternatives.
 */
export function startRecognition(host, id, options) {
  const name = 'voice-engine-recognition';
  if (!SpeechRecognitionImpl) {
    fire(host, name, { id, type: 'error', error: 'recognition-unavailable', message: 'SpeechRecognition is not supported by this browser' });
    fire(host, name, { id, type: 'end' });
    return;
  }
  const opts = options || {};
  const previous = sessions.get(id);
  if (previous) {
    previous.abort();
  }
  const recognition = new SpeechRecognitionImpl();
  recognition.lang = opts.lang || navigator.language;
  recognition.continuous = !!opts.continuous;
  recognition.interimResults = !!opts.interimResults;
  recognition.maxAlternatives = opts.maxAlternatives || 1;
  sessions.set(id, recognition);

  recognition.onstart = () => fire(host, name, { id, type: 'start' });
  recognition.onresult = (e) => {
    const results = [];
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const r = e.results[i];
      const alternatives = [];
      for (let j = 0; j < r.length; j++) {
        alternatives.push({ transcript: r[j].transcript, confidence: r[j].confidence });
      }
      results.push({ index: i, isFinal: r.isFinal, alternatives });
    }
    fire(host, name, { id, type: 'result', results });
  };
  recognition.onerror = (e) => fire(host, name, { id, type: 'error', error: e.error, message: e.message || '' });
  recognition.onend = () => {
    if (sessions.get(id) === recognition) {
      sessions.delete(id);
    }
    fire(host, name, { id, type: 'end' });
  };
  try {
    recognition.start();
  } catch (e) {
    sessions.delete(id);
    fire(host, name, { id, type: 'error', error: 'start-failed', message: String(e && e.message) });
    fire(host, name, { id, type: 'end' });
  }
}

export function stopRecognition(id) {
  const r = sessions.get(id);
  if (r) r.stop();
}

export function abortRecognition(id) {
  const r = sessions.get(id);
  if (r) r.abort();
}
