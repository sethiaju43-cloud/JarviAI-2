# TEST_RESULTS

Date: 2026-11-10.

## 1. Honest status

The author of the code could not compile or run Android code. Nothing below has been executed by the author. The tables show what exists and what the owner must run. Results are filled in only from real runs.

## 2. Automated tests (JVM unit tests, run by GitHub Actions)

| Test file | Covers | Result |
|---|---|---|
| MoodToneTest | mood and depth detection | NOT REPORTED yet |
| SelfCommandsTest | forget / correct / recall / repeat / speed commands, memory matching | NOT REPORTED yet |
| BudgetLogicTest | budget levels, duplicate key, freshness | NOT REPORTED yet |
| UrlGuardTest | HuggingFace host check | NOT REPORTED yet |
| UsageStatsTest | usage text formatting, usage question detection | existing; NOT REPORTED yet |
| ActionWordsTest, LogicTests, MatrixRainMathTest, ModelAutoTest, ModelChainTest, OnlineStreamTest, RouterTests, VoiceHintsTest | existing Jarvis logic | NOT REPORTED yet |

Where to read results: GitHub, Actions, the latest run, artifact "Test-Report"; the run summary says PASS or FAIL.

Logic-level checks the author did run: the matching and detection rules of MoodTone and SelfCommands were ported to Python and run on sample sentences; this found and fixed two bugs (a short word matching inside "sadak"; filler words breaking memory matching). This is not a substitute for the Kotlin tests.

## 3. Manual checklist (the original brief's list)

Mark each as PASS / FAIL / NOT TESTED after trying it on the phone.

| # | Test | Result |
|---|---|---|
| 1 | Local wake word without internet | NOT IMPLEMENTED |
| 2 | Local VAD without internet | NOT IMPLEMENTED |
| 3 | Audio processing (echo cancellation) without internet | NOT IMPLEMENTED |
| 4 | Local TTS without internet (Android system voice) | NOT TESTED |
| 5 | Local STT (Vosk) without internet | NOT TESTED |
| 6 | Local language model answer in airplane mode | NOT TESTED |
| 7 | Cloud fallback when the local model is missing | NOT TESTED |
| 8 | Provider switching when one hits a limit | NOT TESTED |
| 9 | Hindi voice conversation | NOT TESTED |
| 10 | English voice conversation | NOT TESTED |
| 11 | Hindi-English mixed conversation | NOT TESTED |
| 12 | Memory: remember, recall, correct, forget | NOT TESTED |
| 13 | Dashboard counts and budget limit | NOT TESTED |
| 14 | Device disconnection | NOT IMPLEMENTED (no bridge) |
| 15 | Recovery after an error (airplane mode on and off) | NOT TESTED |
| 16 | Permissions and privacy toggles | NOT TESTED |
| 17 | Model integrity (checksum) | NOT IMPLEMENTED |
| 18 | Background service (floating character) | NOT TESTED |
| 19 | Performance: RAM, battery, startup, response time | NOT MEASURED |

## 4. How to record a result

Write date, phone model, Android version, app version, test number, PASS/FAIL, and one line of what happened. Attach the error text for a FAIL.
