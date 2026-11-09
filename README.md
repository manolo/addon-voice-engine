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

`DictationButton` toggles between *Dictate* and *Stop dictation* and updates
the field live while the user speaks. To pick the language and voice:

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
Before that, utterances fail with `SynthesisErrorCode.NOT_ALLOWED`.

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

## Browser support

Speech synthesis works in all modern browsers. Speech recognition works in
Chrome, Edge and Safari, but not in Firefox. Check with
`SpeechRecognition.getSupport()` / `SpeechSynthesis.getSupport()`; they return
`UNKNOWN` until the browser has reported its capabilities, shortly after the
add-on is first used in a UI. When recognition is unavailable, sessions end
with `RecognitionErrorCode.RECOGNITION_UNAVAILABLE` and `DictationButton`
disables itself.

Chrome sends the audio to a Google service for recognition. The available
voices depend on the browser and the operating system.

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
