# Voice Engine Add-on for Vaadin

Speech recognition (speech-to-text) and speech synthesis (text-to-speech) for
Vaadin Flow, using the browser's
[Web Speech API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Speech_API).

- A typed Java API that mirrors the browser API:
  [`SpeechRecognition`](src/main/java/org/vaadin/voiceengine/SpeechRecognition.java) and
  [`SpeechSynthesis`](src/main/java/org/vaadin/voiceengine/SpeechSynthesis.java).
- Ready-made components for the common cases: `DictationButton`,
  `ReadAloudButton`, `LanguageSelect` and `VoiceSelect`.
- No frontend build impact: the add-on has no `@JsModule` or npm dependency,
  so adding it does not require a custom frontend bundle.

Requires Vaadin 25.

## Dictate into a field, read text aloud

```java
TextArea notes = new TextArea("Notes");
add(notes, new DictationButton(notes), new ReadAloudButton(notes));
```

`DictationButton` toggles between *Dictate* and *Stop dictation*, and Escape
cancels and restores the field. The browser starts listening right in the
click handler and writes the text into the field while the user speaks; only
final results go to the server. For push-to-talk in a field's suffix:

```java
TextField field = new TextField();
field.setSuffixComponent(new DictationButton(field) {{
    setTriggerMode(TriggerMode.HOLD);   // listen while held down
    setIconOnly(true);
    setTexts("Hold to dictate", "Listening");
    addThemeVariants(ButtonVariant.TERTIARY);
    setActiveVariants();                // keep the own look while listening
}});
```

To pick the language and voice:

```java
LanguageSelect language = new LanguageSelect();   // languages with a voice, preselects the UI locale
VoiceSelect voice = new VoiceSelect();
language.addValueChangeListener(e -> {
    voice.setLanguage(e.getValue());
    dictationButton.setLang(e.getValue());
    readAloudButton.setLang(e.getValue());
});
voice.addValueChangeListener(e -> readAloudButton.setVoice(e.getValue()));
```

## Speech synthesis

The browser has one speech queue per page, so `SpeechSynthesis` is a static
facade that works on the current UI (overloads take the `UI` explicitly).

```java
SpeechSynthesis.speak("Hello world");

SpeechSynthesis.speak(Utterance.builder("Hei maailma")
                .lang(Locale.of("fi", "FI"))
                .rate(1.2)
                .build(),
        () -> Notification.show("Done"),
        error -> Notification.show("Failed: " + error.code()));

SpeechSynthesis.cancel();                 // also pause() and resume()
SpeechSynthesis.getVoices(voices -> ...); // List<Voice>
SpeechSynthesis.addStateChangeListener(this, state -> ...); // IDLE, SPEAKING, PAUSED
```

Browsers only allow speaking after the user has interacted with the page.
Before that, utterances fail with `SynthesisErrorCode.NOT_ALLOWED`. iOS Safari
is stricter: speech must first be started inside a user gesture. If you speak
later, e.g. when an AI agent's answer arrives, prime the button that starts it:

```java
SpeechSynthesis.prime(askButton);   // speaks a silent utterance on the first click
```

## Speech recognition

A recognition session is bound to an owner component: it is aborted
automatically when the owner detaches.

```java
SpeechRecognition recognition = SpeechRecognition.create(this,
        RecognitionOptions.builder()
                .lang(Locale.of("fi", "FI"))
                .continuous(true)        // keep listening until stop()
                .interimResults(true)    // also report results that may still change
                .build());

recognition.addResultListener(result -> preview.setText(
        recognition.getTranscript() + " " + recognition.getInterimTranscript()));
recognition.addEndListener(transcript -> textArea.setValue(transcript));
recognition.addErrorListener(error -> Notification.show("Recognition failed: " + error.code()));
recognition.addActiveChangeListener(active -> {
    startButton.setEnabled(!active);
    stopButton.setEnabled(active);
});

startButton.addClickListener(e -> recognition.start());
stopButton.addClickListener(e -> recognition.stop());   // abort() discards pending audio
```

For a single phrase:

```java
SpeechRecognition.listenOnce(this, text -> field.setValue(text), error -> { });
```

### Without server round trips

`start()` goes through the server, so listening begins only after a round
trip, and each interim result makes another one. For a responsive UI, let the
browser handle both:

```java
recognition.bindTrigger(micButton, TriggerMode.TOGGLE);  // or HOLD for push-to-talk; Escape aborts
recognition.setLiveTarget(textArea);                     // text written in the browser, final value set on the server at the end
recognition.setInterimResultsToServer(false);            // only final results to the server
```

`isActive()` and all listeners work the same for sessions started by the
trigger. `DictationButton` uses exactly this.

### More options and events

```java
RecognitionOptions.builder()
        .engine(RecognitionEngine.PREFER_ON_DEVICE)  // see "Where speech is processed"
        .phrases(List.of("Vaadin", "SKU-1234"))      // contextual biasing, where supported
        .cancelSpeechOnStart(true)                   // stop reading aloud when the user starts talking
        .build();

recognition.addPhaseListener(phase -> ...);  // AUDIO_START, SPEECH_START, SPEECH_END, NO_MATCH, ...
SpeechRecognition.whenSupportKnown(ui, support -> mic.setVisible(support == SpeechSupport.SUPPORTED));
```

Phase events are only sent by the browser while there is a phase listener.

### Server fallback

Firefox has no speech recognition, and some Chromium builds have the API but
no service behind it. For those, the session can record the audio and let your
server transcribe it, with e.g. Spring AI, OpenAI, Azure or a local Whisper:

```java
recognition.setServerFallback((audio, contentType) -> transcriber.transcribe(audio, contentType));
```

The recording is uploaded when the session is stopped, and the text arrives as
a single final result. The transcriber runs in the upload request without the
session lock, so it can take its time but must not touch components.

## Browser support

Speech synthesis works in all modern browsers. Speech recognition works in
Chrome, Edge and Safari, but not in Firefox. Check with
`SpeechRecognition.getSupport()` / `SpeechSynthesis.getSupport()`; they return
`UNKNOWN` until the browser has reported its capabilities, shortly after the
add-on is first used in a UI. When recognition is unavailable, sessions end
with `RecognitionErrorCode.RECOGNITION_UNAVAILABLE` and `DictationButton`
disables itself.

The available voices depend on the browser and the operating system.

## Where speech is processed

By default the browser decides where speech is recognized: Chrome sends the
audio to a Google service, Edge to a Microsoft one. Newer Chrome and Edge can
also recognize on the device, which keeps the audio private and works offline:

- `RecognitionEngine.ON_DEVICE_ONLY`: never leaves the device; fails if the
  language pack is not installed.
- `RecognitionEngine.PREFER_ON_DEVICE`: on the device when the language pack is
  installed, otherwise the browser's default. A downloadable pack is
  downloaded for the next time.
- `SpeechRecognition.checkOnDeviceAvailability(lang, callback)` and
  `installOnDevice(lang, callback)` for building your own flow.

Only check availability in response to a user action: in Chromium builds
without the on-device service (such as the one Playwright uses), the browser's
check crashes the tab. The add-on therefore treats browsers controlled by
automation (`navigator.webdriver`) as having no on-device recognition.

For speech synthesis, `Voice.localService()` tells whether a voice runs on the
device or sends the text to a service.

## Testing without a browser

`SimulatedSpeech` plays the browser's part in unit tests and browserless
tests:

```java
SimulatedSpeech.start(dictationButton.getRecognition());   // as if the user pressed the button
SimulatedSpeech.finalResult(dictationButton.getRecognition(), "hello world");
SimulatedSpeech.end(dictationButton.getRecognition());
assertEquals("hello world", textArea.getValue());

assertEquals("Done.", SimulatedSpeech.getQueuedUtterances(ui).get(0).text());
SimulatedSpeech.finishUtterance(ui);
```

## How it works

The client-side code is a small ES module (`speech.js`) shipped as a classpath
resource. It is served through a `DownloadHandler` on the UI element and loaded
on demand with a dynamic `import()`. Events from the browser are dispatched on
the UI element and routed to the right Java listeners. This keeps the add-on
free of frontend build dependencies and works the same in every deployment,
including with `@PreserveOnRefresh` and while a modal dialog is open.

## Migrating from 1.x/2.x

3.0 replaces the `VoiceEngine` component with the API above. Typical
replacements:

| 1.x/2.x `VoiceEngine`                           | 3.0                                                         |
|-------------------------------------------------|-------------------------------------------------------------|
| `setButtons(MICROPHONE, PLAY, LANG, VOICE)`     | `DictationButton`, `ReadAloudButton`, `LanguageSelect`, `VoiceSelect` |
| `start()` / `stop()` / `cancel()`               | `SpeechRecognition#start()` / `stop()` / `abort()`, `SpeechSynthesis.cancel()` |
| `addEndListener` + `getRecorded()`              | `SpeechRecognition#addEndListener(transcript -> ...)`        |
| `addResultListener`                             | `SpeechRecognition#addResultListener(result -> ...)`         |
| `setSpeech(text)` + `play()`, `play(text)`      | `SpeechSynthesis.speak(text)` / `speak(Utterance)`           |
| `setLang(...)`, `setVoice(name)`                | `RecognitionOptions.lang`, `Utterance.lang` / `Utterance.voice` |
| `getVoices()`, `addVoiceChangedListener`        | `SpeechSynthesis.getVoices(callback)`                       |
| `setContinuous(true)`                           | `RecognitionOptions.continuous(true)`                       |

If you want to keep the old toolbar, the test sources contain
[`LegacyVoiceEngine`](src/test/java/org/vaadin/addons/voiceengine/LegacyVoiceEngine.java),
a replica of the 1.x/2.x component built on the 3.0 API, which you can copy into
your project.

## Development

```
mvn jetty:run      # demos at http://localhost:8080
mvn verify -Pit    # integration tests with Playwright (headless Chromium)
```

The demo views are in `src/test/java`: typical use with the ready-made
components, the low-level API, and the 1.x/2.x toolbar replica.
