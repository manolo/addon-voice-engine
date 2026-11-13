/*
 * Client side of the WebSpeech add-on: a thin layer on top of the Web Speech
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
 *   web-speech-synthesis   detail: { id, type: start|end|error|pause|resume, error? }
 *   web-speech-recognition detail: { id, type: starting|start|result|phase|error|end, ... }
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
  const name = 'web-speech-synthesis';
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

const RECOGNITION = 'web-speech-recognition';
const UPLOAD_ATTRIBUTE = 'data-web-speech-upload';
const AVAILABILITY_TIMEOUT = 1500;

/** id -> { host, config, target } as last set by configure(). */
const configs = new Map();
/** id -> running session { stop(), abort() }. */
const sessions = new Map();
/** id -> function removing the trigger's listeners. */
const triggers = new Map();
/** id -> function removing the keyboard shortcut's listeners. */
const keyTriggers = new Map();

/**
 * Stores the configuration of a session. Config: lang, continuous,
 * interimResults, maxAlternatives, engine (any|prefer-on-device|on-device-only|
 * on-device-or-server|server),
 * phrases, cancelSpeech, interimToServer, phaseEvents, serverFallback, recordingFormat,
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
  unbindKey(id);
  configs.delete(id);
}

/* Trigger binding: the browser starts the session in the element's event handler. */

/** A tap-or-hold press longer than this (ms) stops listening on release. */
const HOLD_THRESHOLD = 400;

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
  const capture = (e) => {
    try {
      // Keeps the pointerup on the button although the pointer moves away
      if (el.setPointerCapture) el.setPointerCapture(e.pointerId);
    } catch (ignored) {
      // Synthetic pointer events (forwarded shortcuts, tests) have no active pointer
    }
  };
  if (mode === 'hold' || mode === 'tap-or-hold') {
    // hold: listen while pressed. tap-or-hold: a press starts, a short tap
    // keeps listening until the next press, a longer press stops on release.
    let pressStart = 0;
    // A click following a press was handled by the press
    let pressed = false;
    const press = () => {
      pressed = true;
      if (mode === 'tap-or-hold' && sessions.has(id)) {
        pressStart = 0;
        stopRecognition(id);
      } else if (!disabled()) {
        pressStart = Date.now();
        start(id);
      }
    };
    const release = () => {
      if (mode === 'hold' || (pressStart && Date.now() - pressStart >= HOLD_THRESHOLD)) {
        stopRecognition(id);
      }
      pressStart = 0;
    };
    on(el, 'pointerdown', (e) => {
      if (e.button !== 0) return;
      capture(e);
      press();
    });
    on(el, 'pointerup', release);
    on(el, 'pointercancel', release);
    on(el, 'keydown', (e) => {
      if (!isKey(e)) return;
      e.preventDefault();
      if (!e.repeat) press();
    });
    on(el, 'keyup', (e) => {
      if (!isKey(e)) return;
      e.preventDefault();
      release();
    });
    // A long press would open the context menu on touch devices
    on(el, 'contextmenu', (e) => e.preventDefault());
    if (mode === 'tap-or-hold') {
      // A click without a press, e.g. from a screen reader or el.click(), toggles
      on(el, 'click', () => {
        if (pressed) {
          pressed = false;
        } else if (sessions.has(id)) {
          stopRecognition(id);
        } else {
          startUnlessDisabled();
        }
      });
    }
  } else {
    on(el, 'click', () => (sessions.has(id) ? stopRecognition(id) : startUnlessDisabled()));
  }
  on(document, 'keydown', escapeAborts(id), true);
  triggers.set(id, () => cleanups.forEach((c) => c()));
}

/** Escape aborts a running session, once even if several bindings listen. */
function escapeAborts(id) {
  return (e) => {
    const s = sessions.get(id);
    if (e.key === 'Escape' && s && !s.aborted) {
      e.preventDefault();
      e.stopPropagation();
      s.abort();
    }
  };
}

/**
 * Keyboard shortcut anywhere on the page. keys: KeyboardEvent.key or .code
 * values, e.g. ['F8']. mode: toggle (press to start and stop) or hold.
 */
export function bindKey(host, id, keys, mode) {
  unbindKey(id);
  const matches = (e) => keys.includes(e.key) || keys.includes(e.code);
  const down = (e) => {
    if (!matches(e)) return;
    e.preventDefault();
    if (e.repeat) return;
    if (mode === 'hold') {
      start(id);
    } else if (sessions.has(id)) {
      stopRecognition(id);
    } else {
      start(id);
    }
  };
  const up = (e) => {
    if (!matches(e)) return;
    e.preventDefault();
    if (mode === 'hold') stopRecognition(id);
  };
  const escape = escapeAborts(id);
  document.addEventListener('keydown', down, true);
  document.addEventListener('keyup', up, true);
  document.addEventListener('keydown', escape, true);
  keyTriggers.set(id, () => {
    document.removeEventListener('keydown', down, true);
    document.removeEventListener('keyup', up, true);
    document.removeEventListener('keydown', escape, true);
  });
}

export function unbindKey(id) {
  const cleanup = keyTriggers.get(id);
  if (cleanup) {
    keyTriggers.delete(id);
    cleanup();
  }
}

export function unbindTrigger(id) {
  const cleanup = triggers.get(id);
  if (cleanup) {
    triggers.delete(id);
    cleanup();
  }
}

/* Finishing before an action */

/** key -> function removing a finishBefore() binding. */
const finishers = new Map();

/**
 * Clicks on el while the session is active first stop the session; the click
 * is replayed once the session has ended, so the server has the final text
 * before the application's click handler runs.
 */
export function finishBefore(id, el, key) {
  unfinishBefore(key);
  if (!el) return;
  const onClick = (e) => {
    const s = sessions.get(id);
    if (!s || e.webSpeechReplay) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    s.afterEnd.push(() => replayClick(el));
    s.stop();
  };
  el.addEventListener('click', onClick, true);
  finishers.set(key, () => el.removeEventListener('click', onClick, true));
}

export function unfinishBefore(key) {
  const cleanup = finishers.get(key);
  if (cleanup) {
    finishers.delete(key);
    cleanup();
  }
}

function replayClick(el) {
  const e = new MouseEvent('click', { bubbles: true, cancelable: true, composed: true });
  e.webSpeechReplay = true;
  el.dispatchEvent(e);
}

/** Single-line fields, where Enter submits rather than adds a line. */
function isSingleLine(target) {
  const input = target.inputElement || target;
  return input.localName === 'input';
}

/* On-device recognition */

function withTimeout(promise, ms, fallback) {
  return Promise.race([promise, new Promise((resolve) => setTimeout(() => resolve(fallback), ms))]);
}

function langOf(config) {
  return withRegion(config.lang || navigator.language);
}

// Regions for languages whose main region code differs from the language code
const DEFAULT_REGIONS = {
  en: 'US', sv: 'SE', da: 'DK', nb: 'NO', no: 'NO', nn: 'NO', ja: 'JP', ko: 'KR', zh: 'CN', cs: 'CZ',
  el: 'GR', he: 'IL', uk: 'UA', hi: 'IN', ar: 'SA', vi: 'VN', et: 'EE', ca: 'ES', eu: 'ES', gl: 'ES',
  sl: 'SI', sr: 'RS', fa: 'IR', ur: 'PK', ms: 'MY', sq: 'AL', kk: 'KZ', bn: 'IN', ta: 'IN', te: 'IN',
  ga: 'IE', cy: 'GB', af: 'ZA', sw: 'KE', fil: 'PH', tl: 'PH',
};

/**
 * Safari listens but never delivers results for a tag without a region
 * ("en"), so add one: the user's own variant if they have one ("en-GB"),
 * otherwise the language's main region.
 */
function withRegion(tag) {
  if (!tag || tag.includes('-')) return tag;
  const lang = tag.toLowerCase();
  const own = (navigator.languages || []).find((l) => l.toLowerCase().startsWith(lang + '-'));
  if (own) return own;
  return lang + '-' + (DEFAULT_REGIONS[lang] || lang.toUpperCase());
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

  const session = { aborted: false, stopped: false, afterEnd: [], stop() {}, abort() {} };
  sessions.set(id, session);
  // The field's value as the user sees it, which the server may not have yet
  emit(target ? { type: 'starting', original } : { type: 'starting' });
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
    if (removeEnter) removeEnter();
    // After the end event, so the server has the final value first
    session.afterEnd.splice(0).forEach((fn) => fn());
  };
  // Enter in a single-line live target finishes the session first, then
  // reaches the field's own listeners (e.g. a search on Enter)
  let removeEnter = null;
  if (target && isSingleLine(target)) {
    const onEnter = (e) => {
      if (e.key !== 'Enter' || e.webSpeechReplay || e.isComposing) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      session.afterEnd.push(() => {
        ['keydown', 'keypress', 'keyup'].forEach((type) => {
          const replay = new KeyboardEvent(type, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true, composed: true });
          replay.webSpeechReplay = true;
          (target.inputElement || target).dispatchEvent(replay);
        });
      });
      session.stop();
    };
    target.addEventListener('keydown', onEnter, true);
    removeEnter = () => target.removeEventListener('keydown', onEnter, true);
  }
  const phase = (name) => { if (config.phaseEvents) emit({ type: 'phase', phase: name }); };
  const ctx = { id, host, config, session, emit, results, error, end, phase };

  if (config.engine === 'server') {
    if (config.serverFallback) {
      startRecorder(ctx);
    } else {
      error('recognition-unavailable', 'No server fallback is set');
      end();
    }
  } else if (SpeechRecognitionImpl) {
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
  if (config.engine === 'on-device-or-server' && config.serverFallback) {
    // On the device if the language pack is there, otherwise the server,
    // never the browser's cloud service
    const availability = supportsProcessLocally() ? await onDeviceAvailability(lang) : 'unavailable';
    if (availability === 'downloadable') installOnDevice(lang);
    if (session.aborted || session.stopped) {
      if (session.aborted) error('aborted');
      end();
      return;
    }
    if (availability !== 'available') {
      startRecorder(ctx);
      return;
    }
    processLocally = true;
  } else if (config.engine === 'on-device-only' || config.engine === 'on-device-or-server') {
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
  // Chrome applies phrases only on the device and fails the session with
  // phrases-not-supported otherwise, so cloud recognition goes without them
  if (processLocally && config.phrases && config.phrases.length && typeof SpeechRecognitionPhrase !== 'undefined') {
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
    // Language pack missing after all: never fall back to the cloud, but to the server
    const localFailed = processLocally && (e.error === 'service-not-allowed' || e.error === 'language-not-supported');
    const toServer = config.engine === 'on-device-or-server' ? localFailed : FALLBACK_ERRORS.has(e.error);
    if (config.serverFallback && !gotResult && !session.aborted && toServer) {
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

/** Records with MediaRecorder in the browser's own compressed format. */
function mediaRecorder(stream) {
  const recorder = new MediaRecorder(stream);
  const chunks = [];
  recorder.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data); };
  recorder.start();
  return {
    stop: () => new Promise((resolve) => {
      recorder.onstop = () => resolve(new Blob(chunks, { type: recorder.mimeType || 'application/octet-stream' }));
      if (recorder.state !== 'inactive') recorder.stop(); else recorder.onstop();
    })
  };
}

const WAV_RATE = 16000;
const CAPTURE_PROCESSOR = `
registerProcessor('web-speech-capture', class extends AudioWorkletProcessor {
  process(inputs) {
    const channel = inputs[0] && inputs[0][0];
    if (channel) this.port.postMessage(channel.slice(0));
    return true;
  }
});`;

/** Records 16 kHz mono 16-bit PCM WAV, which speech models take as is. */
async function wavRecorder(stream) {
  const context = new AudioContext();
  try {
    const moduleUrl = URL.createObjectURL(new Blob([CAPTURE_PROCESSOR], { type: 'text/javascript' }));
    try {
      await context.audioWorklet.addModule(moduleUrl);
    } finally {
      URL.revokeObjectURL(moduleUrl);
    }
    const source = context.createMediaStreamSource(stream);
    const capture = new AudioWorkletNode(context, 'web-speech-capture');
    const chunks = [];
    capture.port.onmessage = (e) => chunks.push(e.data);
    source.connect(capture);
    return {
      stop: async () => {
        source.disconnect();
        capture.port.onmessage = null;
        const rate = context.sampleRate;
        await context.close();
        return encodeWav(downsample(chunks, rate, WAV_RATE), WAV_RATE);
      }
    };
  } catch (e) {
    context.close();
    throw e;
  }
}

/** Joins the captured chunks and averages them down to the target rate. */
function downsample(chunks, inRate, outRate) {
  const length = chunks.reduce((n, c) => n + c.length, 0);
  const input = new Float32Array(length);
  let offset = 0;
  chunks.forEach((c) => { input.set(c, offset); offset += c.length; });
  if (inRate <= outRate) return input;
  const ratio = inRate / outRate;
  const output = new Float32Array(Math.floor(length / ratio));
  for (let i = 0; i < output.length; i++) {
    const from = Math.floor(i * ratio);
    const to = Math.min(length, Math.floor((i + 1) * ratio));
    let sum = 0;
    for (let j = from; j < to; j++) sum += input[j];
    output[i] = to > from ? sum / (to - from) : 0;
  }
  return output;
}

function encodeWav(samples, rate) {
  const view = new DataView(new ArrayBuffer(44 + samples.length * 2));
  const text = (at, s) => { for (let i = 0; i < s.length; i++) view.setUint8(at + i, s.charCodeAt(i)); };
  text(0, 'RIFF');
  view.setUint32(4, 36 + samples.length * 2, true);
  text(8, 'WAVE');
  text(12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);        // PCM
  view.setUint16(22, 1, true);        // mono
  view.setUint32(24, rate, true);
  view.setUint32(28, rate * 2, true); // bytes per second
  view.setUint16(32, 2, true);        // bytes per frame
  view.setUint16(34, 16, true);       // bits per sample
  text(36, 'data');
  view.setUint32(40, samples.length * 2, true);
  for (let i = 0; i < samples.length; i++) {
    const v = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(44 + i * 2, v < 0 ? v * 0x8000 : v * 0x7fff, true);
  }
  return new Blob([view], { type: 'audio/wav' });
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
  let recorder;
  try {
    recorder = ctx.config.recordingFormat === 'wav' ? await wavRecorder(stream) : mediaRecorder(stream);
  } catch (e) {
    release();
    error('audio-capture', String(e && e.message));
    end();
    return;
  }
  const finish = async () => {
    const blob = await recorder.stop();
    release();
    phase('audioend');
    if (session.aborted) {
      error('aborted');
      end();
      return;
    }
    try {
      const url = new URL(host.getAttribute(UPLOAD_ATTRIBUTE), document.baseURI);
      url.searchParams.set('session', id);
      url.searchParams.set('lang', langOf(ctx.config));
      (ctx.config.phrases || []).forEach((p) => url.searchParams.append('phrase', p));
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
  if (session.aborted || session.stopped) {
    // Stopped while the recorder was being set up: nothing worth sending
    await recorder.stop();
    release();
    if (session.aborted) error('aborted');
    end();
    return;
  }
  let finishing = false;
  const once = () => { if (!finishing) { finishing = true; finish(); } };
  session.stop = () => { session.stopped = true; once(); };
  session.abort = () => { session.aborted = true; once(); };
  emit({ type: 'start', processLocally: false, server: true });
  phase('audiostart');
}
