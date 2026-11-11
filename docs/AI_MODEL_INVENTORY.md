# AI_MODEL_INVENTORY

Source: Maya_v5.2.56(245)_base_src (decompiled APK). Sizes are measured from the extracted files.
Status of each claim: VERIFIED = seen directly in files; NOT VERIFIED = could not be established here.

## 1. Bundled model files (inside the APK)

| Model | Path | Size (bytes) | Purpose | Runtime | Offline | Evidence |
|---|---|---|---|---|---|---|
| Silero VAD v4 | assets/guardian/silero_vad.tflite | 663,596 | Speech / not-speech gate, driven frame by frame with LSTM state | TFLite | Yes | assets/guardian/README.txt says "Silero VAD v4 ... LSTM state" |
| DTLN-AEC part 1 | assets/dtln/dtln_aec_128_1.tflite | 1,996,600 | Echo cancellation (stage 1) | TFLite | Yes | file present; licence file dtln-aec-mit.txt |
| DTLN-AEC part 2 | assets/dtln/dtln_aec_128_2.tflite | 5,277,012 | Echo cancellation (stage 2) | TFLite | Yes | same |
| Wake-word melspectrogram | assets/wakeword/melspectrogram.tflite | 1,092,540 | Audio feature extraction | TFLite | Yes | file present |
| Wake-word embedding | assets/wakeword/embedding_model.tflite | 1,330,312 | Audio embedding | TFLite | Yes | file present |
| "Hey Maya" detector | assets/wakeword/hey_maya.tflite | 638,004 | Wake phrase classifier | TFLite | Yes | file present |
| "Hello Maya" detector | assets/wakeword/hello_maya.tflite | 638,004 | Wake phrase classifier | TFLite | Yes | file present |
| "Wake up Maya" detector | assets/wakeword/wake_up_maya.tflite | 1,303,348 | Wake phrase classifier | TFLite | Yes | file present |
| ML Kit barcode (3 files) | assets/mlkit_barcode_models/*.tflite | 390,456 + 213,880 + 276,552 | Barcode scanning (not conversational AI) | TFLite / ML Kit | Yes | files present |

## 2. Downloaded (not bundled)

| Model | Source | Size | Evidence |
|---|---|---|---|
| Speaker recognition "Powerful" (ReDimNet2-B6) + cohort | https://getmaya.online/models/guardian/ (rdn2b6s.tflite, cohort_rdn2b6s.bin) | 27,050,368 + 153,616 | assets/guardian/models.json (sha256 listed) |
| Speaker recognition "Medium" (ECAPA-TDNN) | same server (ecapa6s.tflite, cohort.bin) | not in the manifest copy | assets/guardian/README.txt (describes it; the bundled manifest lists only Powerful) |
| Pocket TTS model weights (Kyutai / Indus Hindi) | not found in the archive | NOT VERIFIED | only the native runtime was found; see section 3 |

Note: these model files are hosted on Maya's own server. Using that server from Jarvis is not authorised (see MODEL_LICENSE_REPORT.md).

## 3. Native libraries (arm64-v8a)

| Library | Size (bytes) | What the name/licences suggest | Status |
|---|---|---|---|
| libtensorflowlite_jni.so | 3,401,664 | TensorFlow Lite runtime | VERIFIED present |
| libonnxruntime.so | 17,571,152 | ONNX Runtime (onnxruntime-mit.txt present) | VERIFIED present |
| libpockettts_jni.so | 1,037,088 | Pocket TTS runtime (pockettts-cpp-mit.txt present) | VERIFIED present; model weights NOT VERIFIED |
| libmaya_aec3.so | 444,808 | Echo cancellation (webrtc-bsd-3.txt present, so likely WebRTC AEC3 based) | name and licence only; NOT VERIFIED by code |
| libhunterstt_jni.so | 335,824 | Possibly a speech-to-text runtime | NOT VERIFIED (name only) |
| libturndetector_jni.so | 329,224 | Possibly end-of-turn detection | NOT VERIFIED (name only) |
| libbarhopper_v3.so | 4,946,720 | ML Kit barcode | VERIFIED present |

## 4. What was NOT established

- Input and output tensor shapes of every .tflite file (no TensorFlow tooling available in the analysis environment).
- Whether hunterstt is local speech recognition.
- Which Pocket TTS weight file Maya really loads at runtime and where it comes from.
- Real-world latency, RAM and battery numbers (nothing was run on a device).

## 5. Jarvis today (for comparison)

- Local LLM: LiteRT-LM models listed in LocalModelCatalog.kt (0.6B, 1.5B, 1.5B, ~2B and ~4B effective sizes; 586 MB to 3650 MB).
- Offline speech recognition: Vosk (OfflineVoice.kt).
- No TFLite interpreter dependency, no ONNX runtime, no wake-word, no local neural TTS.
