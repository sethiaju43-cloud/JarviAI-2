# MODEL_LICENSE_REPORT

This is an engineering review, not legal advice. Check the original upstream licence text before shipping.

## 1. Licence files found inside Maya (assets/licences/)

| File | Covers | Licence | Obligation if Jarvis reuses it |
|---|---|---|---|
| dtln-aec-mit.txt | DTLN-AEC (Nils L. Westhausen, 2020) | MIT | Keep copyright + licence notice |
| kyutai-pocket-tts-code-mit.txt | Kyutai Pocket TTS code | MIT | Keep notice |
| pockettts-cpp-mit.txt | PocketTTS.cpp runtime | MIT | Keep notice |
| indus-pocket-tts-licence.txt | Indus Pocket TTS weights | CC-BY-4.0 (derivative of Kyutai Pocket TTS, also CC-BY-4.0); code in that repo MIT | Credit, link licence, state changes; follow Kyutai prohibited-use terms (no voice impersonation or cloning without consent, no fraudulent or deceptive calls) |
| pocket-tts-hindi-notice.txt | Saryps Labs Pocket TTS Hindi (ONNX INT8) | Weights CC BY 4.0; datasets AI4Bharat IndicVoices and Kathbath (CC BY 4.0 per their cards) | Suggested attribution: "Pocket TTS Hindi by Saryps Labs, based on Kyutai Pocket TTS and trained with AI4Bharat IndicVoices and Kathbath data. Licensed under CC BY 4.0." |
| onnxruntime-mit.txt, onnxruntime-third-party.txt | ONNX Runtime | MIT + third-party notices | Ship notices |
| sentencepiece-apache-2.0.txt | SentencePiece | Apache-2.0 | Keep notice |
| webrtc-bsd-3.txt | WebRTC | BSD-3-Clause | Keep notice |
| cc-by-4.0.txt | full CC-BY-4.0 text | n/a | n/a |
| fluentui-emoji-mit.txt, nunito-ofl.txt, jetbrains-mono-ofl.txt | emoji and fonts | MIT / OFL | Keep notices |

## 2. Components with NO licence file in the archive

| Component | Finding | Consequence |
|---|---|---|
| silero_vad.tflite | No Silero licence file found in assets/licences/. Upstream Silero VAD is published under MIT (snakers4/silero-vad) - confirm upstream. | Take the model from upstream, not from Maya's copy, and ship the MIT notice. |
| Wake-word models (melspectrogram, embedding, detectors) | No licence file. The structure (mel + embedding + small classifiers) resembles the openWakeWord design, but this was NOT verified in code. Some openWakeWord pre-trained models are reported to carry a non-commercial licence: confirm upstream. | Do not reuse Maya's files. Train Jarvis' own detector. |
| TensorFlow Lite runtime | No licence file. Upstream is Apache-2.0 (confirm). | Use the official Maven artifact. |
| Speaker models (rdn2b6s, ecapa6s) | Hosted on Maya's server; no licence text seen. | Not authorised for Jarvis. Use a separately licensed open model if speaker recognition is built. |
| libmaya_aec3.so, libhunterstt_jni.so, libturndetector_jni.so, all smali code | Maya's own implementation. No licence granting reuse was found. | Proprietary. Do not copy. Clean-room only. |

## 3. Decision for Jarvis

1. Do not copy Maya's code, native libraries, server URLs or hosted model files.
2. Use upstream open projects directly, with their notices: Silero VAD (MIT), DTLN-AEC (MIT), Kyutai Pocket TTS / Indus or Saryps Hindi (CC-BY-4.0 weights, MIT code), ONNX Runtime (MIT).
3. Add an in-app "Open source notices" screen and a NOTICE file before any of these ship.
4. If Jarvis voices are ever cloned or customised, follow the Kyutai prohibited-use terms above.
5. The Maya name, avatar and UI assets are Maya's branding; Jarvis should use its own.
