# OFFLINE_AI_COMPATIBILITY

Responsibilities are kept separate on purpose:
VAD is not speech recognition. Wake-word detection is not an LLM. Pocket TTS is not a conversational model.

## Current Jarvis baseline (verified in the source)

- Offline STT: Vosk (OfflineVoice.kt), Hindi + English.
- Local LLM: LiteRT-LM models, loaded and unloaded by LocalLlm.kt, chosen by ModelRouter.kt.
- TTS: Android system TTS (VoiceHelper.kt) and a Gemini TTS path (GeminiVoice.kt).
- Barge-in (interrupting speech): a bargeIn object is used in ChatActivity.kt and FloatingService.kt.
- No TFLite interpreter, no ONNX Runtime, no wake-word, no speaker recognition.

## Component decisions

| Component | Compatible with Jarvis? | What is needed | Value today | Decision |
|---|---|---|---|---|
| Silero VAD | Yes, from the upstream MIT model | A TFLite/ONNX runtime dependency (version must be checked in CI) and a bundled model file | Low now: Vosk already ends utterances itself and Jarvis has no cloud STT | Postpone until wake-word work (VAD gates the wake-word and saves battery) |
| DTLN-AEC | Possible, MIT | Runtime dependency, real-time audio loop, tuning per phone | Only needed for always-listening and talking over speaker output | Postpone. Check the existing bargeIn first |
| Wake word "Jarvis" | Maya's detectors are NOT usable: they are trained only for Hey / Hello / Wake up Maya | Train a new detector for "Jarvis" (needs recordings or synthetic speech and a training run), plus a foreground service with a microphone notification | High, but needs work outside this environment | Plan (Stage 4). Needs the owner to run a training job |
| Pocket TTS (English/Hindi) | Feasible. Weights CC-BY-4.0, code MIT | ONNX/native runtime, the model weights (not found in the archive), JNI wrapper, attribution screen, size and RAM test | High for "human-like" voice | Plan (Stage 5). Official model link and size must be confirmed first |
| Speaker recognition | Maya's hosted models: not authorised | An independently licensed model, enrolment UI, privacy controls | Medium | Later. Optional and opt-in |
| Local STT beyond Vosk | Not investigated yet | A lightweight multilingual model would need testing on real devices | Medium | Later |
| ML Kit barcode | Irrelevant | none | none | Skip |

## Device and cost notes

- Local inference is not free: it uses CPU, RAM, storage and battery. The Jarvis dashboard says this too.
- Models must be loaded on demand and unloaded when idle (Jarvis already has an idle-unload setting).
- Nothing in this document has been benchmarked on a phone. All performance statements are open items.

## Open items

1. Read tensor shapes of every candidate .tflite from the upstream source, not from Maya.
2. Confirm TFLite/LiteRT dependency version works next to the existing LiteRT-LM dependency (CI build).
3. Confirm the Pocket TTS Hindi model files, sizes and runtime requirements at the official upstream.
4. Measure battery use of an always-on wake-word service before enabling it by default.
