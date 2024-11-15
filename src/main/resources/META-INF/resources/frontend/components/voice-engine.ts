import { LitElement, css, html } from 'lit';
import { customElement, property } from 'lit/decorators.js';

// voices with parentesis sounds as very synthetic voice
const preferVoicePattern = /^[^\(]+$/;
// if connection is not good enough use localvoices

@customElement('voice-engine')
export class SpeechConverter extends LitElement {
  // add styles to the LitElement component
  static styles = css`
    html .voice-engine-overlay {
      color: green;
    }
  `;
  render() {
    return html`<slot></slot>`;
  }

  @property({ type: Boolean })
  continuous = false;
  @property()
  recorded?: string;
  @property({ type: Boolean })
  isRecording = false;
  @property()
  lang = navigator.language;
  @property({ type: Boolean })
  localService = true;
  @property()
  speech?: string;
  @property()
  voices: any;
  @property()
  voice?: string;

  recognition: any;
  recordIndex = 0;

  syntesis: any;
  speechVoices: any;
  speechVoice: any;
  syntTimer: any;
  changingLang = false;

  constructor() {
    super();
    const $wnd = window as any;
    const SpeechRecognition =
      $wnd.SpeechRecognition ||
      $wnd.webkitSpeechRecognition ||
      $wnd.mozSpeechRecognition ||
      $wnd.msSpeechRecognition ||
      $wnd.oSpeechRecognition;

    this.recognition =
      SpeechRecognition !== undefined
        ? new SpeechRecognition()
        : console.error('voice-engine: Your browser does not support the Web SpeechRecognition API');

    const speechSynthesis = $wnd.speechSynthesis;
    this.syntesis =
      speechSynthesis !== undefined
        ? speechSynthesis
        : console.error('voice-engine: Your browser does not support the Web SpeechSynthesis API');
  }

  async connectedCallback() {
    await super.connectedCallback();
    this.classList.add('voice-engine');
    this.initializeAttributes();
    this.initializeEventListeners();
    let cont = 0;
    // Voices take a little bit to load
    return new Promise((resolve) => {
      const id = setTimeout(() => {
        if (cont++ > 100) {
          clearTimeout(id);
          console.error('voice-engine: Voices not loaded');
          resolve(null);
        }
        this.speechVoices = this.syntesis.getVoices().sort((a:any, b:any) =>
          preferVoicePattern.test(a.name) !== preferVoicePattern.test(b.name) ? Number(preferVoicePattern.test(b.name)) - Number(preferVoicePattern.test(a.name)) :
          a.name.localeCompare(b.name) );
        if (this.speechVoices.length > 0) {
          this.onLangChanged();
          clearTimeout(id);
          resolve(null)
        } else {
          console.debug('voice-engine: waiting for voices to be loaded...');
        }
      }, 1);
    });
  }

  requestUpdate(name?: PropertyKey, oldValue?: unknown) {
    if (name && oldValue) {
      if (name == "lang" && !this.changingLang && this.lang !== oldValue) {
        this.onLangChanged();
      }
      if (name == "voice" && !this.changingLang && this.lang !== oldValue) {
        this.onLangChanged();
      }
    }
    return super.requestUpdate(name, oldValue);
  }

  initializeAttributes() {
    if (this.hasAttribute('speech')) {
      this.speech = this.getAttribute('speech') || '';
    }
    if (this.hasAttribute('continuous')) {
      this.continuous = true;
    }
    if (this.hasAttribute('lang')) {
      this.lang = this.getAttribute('lang') || 'en-US';
    }
    if (this.hasAttribute('localService')) {
      this.localService = true;
    }
    if (this.recognition) {
      this.recognition.continuous = this.continuous;
      this.recognition.interimResults = false;
      this.recognition.lang = this.lang;
    }
  }

  initializeEventListeners() {
    if (!this.recognition) {
      console.error('voice-engine: Recognition not initialized');
      return;
    }

    ['start', 'end', 'error', 'result', 'speechResult'].forEach((eventName) =>
      this.recognition.addEventListener(eventName, (e: any) => {
        console.debug(`voice-engine: Event -> ${eventName}`, e);
        if (eventName === 'end') {
          if (!this.isRecording) {
            return;
          }
          this.stopRecording();
        } else if (eventName === 'result') {
          const newResults = [...Array(e.results.length).keys()]
            .slice(this.recordIndex)
            .map((i) => e.results[i][0].transcript);
          this.recorded = this.recorded + newResults.join(' ');
          this.recordIndex = e.results.length;
          this.dispatchEvent(new CustomEvent('recorded'));
        }
        this.dispatchEvent(new CustomEvent(eventName, { detail: e }));
      })
    );
  }

  startRecording() {
    if (!this.isRecording) {
      this.recorded = "";
      this.recognition.start();
      this.isRecording = true;
      this.recordIndex = 0;
    } else {
      this.stopRecording();
    }
  }

  stopRecording() {
    this.recognition.stop();
    this.isRecording = false;
    this.syntesis.pause();
  }

  async playSpeech() {
    this.cancel();
    if (!this.speechVoice) {
      this.onLangChanged();
    }
    if (this.speech) {
      console.debug(`voice-engine: Speaking ${this.voice}: ${this.speech}`);
      await this._speak(this.speech);
      return;
    }
  }

  async _hear() {
    return new Promise((resolve, reject) => {
      this.recognition.start();
      this.recognition.onresult = e => {
          var current = e.resultIndex;
          var transcript = e.results[current][0].transcript;
          console.log(transcript);
          this.recognition.stop();
          resolve(transcript);
      }
    });
  }

  async _speak(text: string) {
    // enclose in a promise to be able to await
    return new Promise((resolve) => {
      const speaker = new SpeechSynthesisUtterance(text);
      speaker.lang = this.lang;
      speaker.voice = this.speechVoice;
        // See https://stackoverflow.com/a/57672147/280410
      this.syntTimer = setInterval(() => {
        if (!this.syntesis.speaking) {
          clearInterval(this.syntTimer);
          resolve(null);
        } else {
          this.syntesis.pause();
          this.syntesis.resume();
        }
      }, 140000);
      this.syntesis.speak(speaker);
      speaker.onend = () => {
        resolve(null);
        clearInterval(this.syntTimer);
      };
    });
  }

  onLangChanged() {
    const speechVoices = this.speechVoices.sort((a: any, b: any) => Number(b.localService === this.localService) - Number(a.localService === this.localService));

    this.voices = this.speechVoices.map((x: any) => { return { name: x.name, lang: x.lang } })

    if (this.recognition) {
      this.recognition.lang = this.lang;
    }
    let filteredVoices = speechVoices.filter((v: any) => v.lang === this.lang);

    if (filteredVoices.length === 0 && this.lang.includes('-')) {
      filteredVoices = speechVoices.filter((v: any) => v.lang.split('-')[0] === this.lang.split('-')[0]);
    }
    if (filteredVoices.length === 0) {
      filteredVoices = speechVoices.filter((v: any) => v.lang === 'en-US');
    }

    // Selects the voice from the filteredVoices that matches this.voice if it exists otherwise the first one
    this.speechVoice = filteredVoices.filter((voice: any) => voice.name == this.voice)[0];

    // Else select the first in the list for the language
    if (!this.speechVoice) {
      this.speechVoice = filteredVoices[0];
    }

    if (!this.speechVoice) {
      console.error(`voice-engine: Unable to select an appropriate voice for language=${this.lang}`);
    } else {
      this.changingLang = true;
      this.voice = this.speechVoice.name;
      this.changingLang = false;
      this.dispatchEvent(new CustomEvent('voice-changed'));
      console.log(`voice-engine: Selected voice: ${this.voice} for language=${this.lang}`);
    }
  }

  cancel() {
    if (this.recognition) {
      this.recognition.abort();
    }
    this.syntesis.pause();
    this.syntesis.cancel();
    if (this.syntTimer) {
      clearInterval(this.syntTimer);
      this.syntTimer = null;
    }
  }
}