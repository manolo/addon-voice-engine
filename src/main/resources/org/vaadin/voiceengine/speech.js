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
 *   voice-engine-recognition detail: { id, type: starting|start|result|phase|error|end, ... }
 *
 * A recognition session can be started from the server (startRecognition) or
 * directly in the browser by a bound trigger element (bindTrigger), which
 * avoids the server round trip and keeps the user activation. "starting" is
 * fired synchronously in both cases, so the server learns about client-started
 * sessions.
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

/*
 * Some browsers (iOS Safari) only allow speaking if speech was first started
 * inside a user gesture. prime() speaks a silent, empty utterance in the first
 * gesture on the element, after which the server may speak at any time.
 */
let primed = false;
const primeTypes = ['click', 'pointerup', 'keydown'];
const primers = new WeakMap();

function primeNow() {
  if (!primed && synth && typeof SpeechSynthesisUtterance !== 'undefined') {
    primed = true;
    const u = new SpeechSynthesisUtterance('');
    u.volume = 0;
    synth.speak(u);
  }
}

export function prime(el) {
  if (!el || primed || primers.has(el)) return;
  const handler = () => {
    primeNow();
    primeTypes.forEach((t) => el.removeEventListener(t, handler, true));
    primers.delete(el);
  };
  primers.set(el, handler);
  primeTypes.forEach((t) => el.addEventListener(t, handler, true));
}

export function unprime(el) {
  const handler = el && primers.get(el);
  if (handler) {
    primeTypes.forEach((t) => el.removeEventListener(t, handler, true));
    primers.delete(el);
  }
}

/* -------------------------------------------------------------- recognition */

const RECOGNITION = 'voice-engine-recognition';
const UPLOAD_ATTRIBUTE = 'data-voice-engine-upload';
const AVAILABILITY_TIMEOUT = 1500;

/** id -> { host, config, target } as last set by configure(). */
const configs = new Map();
/** id -> running session { stop(), abort() }. */
const sessions = new Map();
/** id -> function removing the trigger's listeners. */
const triggers = new Map();

/**
 * Stores the configuration of a session. Config: lang, continuous,
 * interimResults, maxAlternatives, engine (any|prefer-on-device|on-device-only),
 * phrases, cancelSpeech, interimToServer, phaseEvents, serverFallback,
 * liveAppend. target: optional element whose value is updated live.
 */
export function configure(host, id, config, target) {
  configs.set(id, { host, config: config || {}, target: target || null });
}

export function startRecognition(host, id, config, target) {
  configure(host, id, config, target);
  start(id);
}

export function stopRecognition(id) {
  const s = sessions.get(id);
  if (s) s.stop();
}

export function abortRecognition(id) {
  const s = sessions.get(id);
  if (s) s.abort();
}

export function isActive(id) {
  return sessions.has(id);
}

/** Forgets a session, e.g. when its owner is detached. */
export function dispose(id) {
  abortRecognition(id);
  unbindTrigger(id);
  configs.delete(id);
}

/* Trigger binding: the browser starts the session in the element's event handler. */

export function bindTrigger(host, el, id, mode) {
  unbindTrigger(id);
  if (!el) return;
  const cleanups = [];
  const on = (target, type, fn, opts) => {
    target.addEventListener(type, fn, opts);
    cleanups.push(() => target.removeEventListener(type, fn, opts));
  };
  const isKey = (e) => e.key === ' ' || e.key === 'Enter';
  const disabled = () => el.disabled || el.hasAttribute('disabled');
  const startUnlessDisabled = () => { if (!disabled()) start(id); };
  if (mode === 'hold') {
    on(el, 'pointerdown', (e) => {
      if (e.button !== 0) return;
      if (disabled()) return;
      if (el.setPointerCapture) el.setPointerCapture(e.pointerId);
      start(id);
    });
    on(el, 'pointerup', () => stopRecognition(id));
    on(el, 'pointercancel', () => stopRecognition(id));
    on(el, 'keydown', (e) => {
      if (!isKey(e)) return;
      e.preventDefault();
      if (!e.repeat) startUnlessDisabled();
    });
    on(el, 'keyup', (e) => { if (isKey(e)) stopRecognition(id); });
    // A long press would open the context menu on touch devices
    on(el, 'contextmenu', (e) => e.preventDefault());
  } else {
    on(el, 'click', () => (sessions.has(id) ? stopRecognition(id) : startUnlessDisabled()));
  }
  on(document, 'keydown', (e) => {
    if (e.key === 'Escape' && sessions.has(id)) {
      e.preventDefault();
      e.stopPropagation();
      abortRecognition(id);
    }
  }, true);
  triggers.set(id, () => cleanups.forEach((c) => c()));
}

export function unbindTrigger(id) {
  const cleanup = triggers.get(id);
  if (cleanup) {
    triggers.delete(id);
    cleanup();
  }
}

/* On-device recognition */

function withTimeout(promise, ms, fallback) {
  return Promise.race([promise, new Promise((resolve) => setTimeout(() => resolve(fallback), ms))]);
}

function langOf(config) {
  return config.lang || navigator.language;
}

/*
 * SpeechRecognition.available() and install() crash the tab in Chromium builds
 * without the on-device service, e.g. the Chromium used by Playwright (and
 * reportedly some Electron and Brave builds). A timeout cannot help with a
 * crash, so they are only called when asked, never in automated browsers
 * (navigator.webdriver), where the application's own end-to-end tests would
 * die, and no answer in time counts as unavailable.
 */
function onDeviceApi(name) {
  return !!SpeechRecognitionImpl && typeof SpeechRecognitionImpl[name] === 'function' && !navigator.webdriver;
}

/**
 * Whether the language can be recognized on the device: available,
 * downloadable, downloading or unavailable.
 */
export async function onDeviceAvailability(lang) {
  if (!onDeviceApi('available')) {
    return 'unavailable';
  }
  try {
    const result = await withTimeout(
      SpeechRecognitionImpl.available({ langs: [lang || navigator.language], processLocally: true }),
      AVAILABILITY_TIMEOUT, 'unavailable');
    return typeof result === 'string' ? result : 'unavailable';
  } catch (e) {
    return 'unavailable';
  }
}

/** Downloads the language pack. Needs a user activation if a download starts. */
export async function installOnDevice(lang) {
  if (!onDeviceApi('install')) {
    return false;
  }
  try {
    return !!(await SpeechRecognitionImpl.install({ langs: [lang || navigator.language], processLocally: true }));
  } catch (e) {
    return false;
  }
}

function supportsProcessLocally() {
  return !!SpeechRecognitionImpl && 'processLocally' in SpeechRecognitionImpl.prototype && !navigator.webdriver;
}

/* Sessions */

function join(base, text) {
  if (!text) return base;
  if (!base) return text;
  return /\s$/.test(base) ? base + text : base + ' ' + text;
}

function joinParts(parts) {
  return parts.map((p) => p.trim()).filter((p) => p).join(' ');
}

// Errors after which the server fallback is tried instead, if configured
const FALLBACK_ERRORS = new Set(['network', 'service-not-allowed', 'language-not-supported']);

function start(id) {
  const c = configs.get(id);
  if (!c || sessions.has(id)) return;
  const { host, config, target } = c;
  const emit = (detail) => fire(host, RECOGNITION, { id, ...detail });

  // original is restored on abort or when nothing was recognized, base is
  // what the text is joined to (empty when replacing the value)
  const original = target ? target.value || '' : null;
  const base = target ? (config.liveAppend ? original : '') : null;
  const finals = new Map();
  const interims = new Map();
  let errored = null;
  let ended = false;

  const session = { aborted: false, stopped: false, stop() {}, abort() {} };
  sessions.set(id, session);
  emit({ type: 'starting' });
  if (config.cancelSpeech) cancel();

  const updateTarget = () => {
    if (target) {
      const sorted = (m) => [...m.entries()].sort((a, b) => a[0] - b[0]).map((e) => e[1]);
      target.value = join(base, joinParts([...sorted(finals), ...sorted(interims)]));
    }
  };
  const results = (list) => {
    if (ended) return;
    for (const r of list) {
      if (r.isFinal) {
        interims.delete(r.index);
        finals.set(r.index, r.alternatives[0] ? r.alternatives[0].transcript : '');
      } else {
        interims.set(r.index, r.alternatives[0] ? r.alternatives[0].transcript : '');
      }
    }
    updateTarget();
    const toServer = config.interimToServer === false ? list.filter((r) => r.isFinal) : list;
    if (toServer.length) emit({ type: 'result', results: toServer });
  };
  const error = (code, message) => {
    errored = errored || code;
    emit({ type: 'error', error: code, message: message || '' });
  };
  const end = () => {
    if (ended) return;
    ended = true;
    if (sessions.get(id) === session) sessions.delete(id);
    if (target) {
      interims.clear();
      if (session.aborted || errored === 'aborted' || !joinParts([...finals.values()])) {
        target.value = original;
      } else {
        updateTarget();
      }
    }
    emit({ type: 'end' });
  };
  const phase = (name) => { if (config.phaseEvents) emit({ type: 'phase', phase: name }); };
  const ctx = { id, host, config, session, emit, results, error, end, phase };

  if (SpeechRecognitionImpl) {
    startBrowser(ctx);
  } else if (config.serverFallback) {
    startRecorder(ctx);
  } else {
    error('recognition-unavailable', 'SpeechRecognition is not supported by this browser');
    end();
  }
}

async function startBrowser(ctx) {
  const { config, session, emit, results, error, end, phase } = ctx;
  let recognition = null;
  let gotResult = false;
  let fallback = false;
  session.stop = () => {
    session.stopped = true;
    if (recognition) recognition.stop(); else end();
  };
  session.abort = () => {
    session.aborted = true;
    if (recognition) {
      recognition.abort();
    } else {
      error('aborted');
      end();
    }
  };

  const lang = langOf(config);
  let processLocally = false;
  if (config.engine === 'on-device-only') {
    if (!supportsProcessLocally()) {
      error('on-device-unavailable', 'This browser cannot recognize speech on the device');
      end();
      return;
    }
    processLocally = true;
  } else if (config.engine === 'prefer-on-device' && supportsProcessLocally()) {
    const availability = await onDeviceAvailability(lang);
    if (availability === 'available') {
      processLocally = true;
    } else if (availability === 'downloadable') {
      // Use the cloud this time, the device next time
      installOnDevice(lang);
    }
    if (session.aborted || session.stopped) {
      if (session.aborted) error('aborted');
      end();
      return;
    }
  }

  recognition = new SpeechRecognitionImpl();
  recognition.lang = lang;
  recognition.continuous = !!config.continuous;
  recognition.interimResults = !!config.interimResults;
  recognition.maxAlternatives = config.maxAlternatives || 1;
  if (processLocally) recognition.processLocally = true;
  if (config.phrases && config.phrases.length && typeof SpeechRecognitionPhrase !== 'undefined') {
    recognition.phrases = config.phrases.map((p) => new SpeechRecognitionPhrase(p));
  }

  recognition.onstart = () => emit({ type: 'start', processLocally });
  recognition.onresult = (e) => {
    gotResult = true;
    const list = [];
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const r = e.results[i];
      const alternatives = [];
      for (let j = 0; j < r.length; j++) {
        alternatives.push({ transcript: r[j].transcript, confidence: r[j].confidence });
      }
      list.push({ index: i, isFinal: r.isFinal, alternatives });
    }
    results(list);
  };
  recognition.onerror = (e) => {
    if (config.serverFallback && !gotResult && !session.aborted && FALLBACK_ERRORS.has(e.error)) {
      fallback = true;
      return;
    }
    error(e.error, e.message);
  };
  recognition.onend = () => {
    if (fallback && !session.aborted && !session.stopped) {
      startRecorder(ctx);
    } else {
      end();
    }
  };
  ['audiostart', 'soundstart', 'speechstart', 'speechend', 'soundend', 'audioend', 'nomatch']
    .forEach((name) => { recognition['on' + name] = () => phase(name); });
  try {
    recognition.start();
  } catch (e) {
    error('start-failed', String(e && e.message));
    end();
  }
}

/**
 * Server fallback: records audio with MediaRecorder and uploads it to the
 * server, which transcribes it. Used when the browser cannot recognize speech.
 */
async function startRecorder(ctx) {
  const { id, host, session, emit, results, error, end, phase } = ctx;
  session.stop = () => { session.stopped = true; };
  session.abort = () => { session.aborted = true; };
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || typeof MediaRecorder === 'undefined') {
    error('recognition-unavailable', 'This browser can neither recognize nor record speech');
    end();
    return;
  }
  let stream;
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  } catch (e) {
    error(e && e.name === 'NotAllowedError' ? 'not-allowed' : 'audio-capture', String(e && e.message));
    end();
    return;
  }
  const release = () => stream.getTracks().forEach((t) => t.stop());
  if (session.aborted || session.stopped) {
    release();
    if (session.aborted) error('aborted');
    end();
    return;
  }
  const recorder = new MediaRecorder(stream);
  const chunks = [];
  recorder.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data); };
  recorder.onstop = async () => {
    release();
    phase('audioend');
    if (session.aborted) {
      error('aborted');
      end();
      return;
    }
    try {
      const blob = new Blob(chunks, { type: recorder.mimeType || 'application/octet-stream' });
      const url = new URL(host.getAttribute(UPLOAD_ATTRIBUTE), document.baseURI);
      url.searchParams.set('session', id);
      const response = await fetch(url, { method: 'POST', body: blob, headers: { 'Content-Type': blob.type } });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const transcript = ((await response.json()).transcript || '').trim();
      if (transcript) {
        results([{ index: 0, isFinal: true, alternatives: [{ transcript, confidence: 1 }] }]);
      }
    } catch (e) {
      error('transcription-failed', String(e && e.message));
    }
    end();
  };
  const finish = () => { if (recorder.state !== 'inactive') recorder.stop(); };
  session.stop = () => { session.stopped = true; finish(); };
  session.abort = () => { session.aborted = true; finish(); };
  recorder.start();
  emit({ type: 'start', processLocally: false, server: true });
  phase('audiostart');
}
