# JARVI AI

Android par hybrid personal assistant: **offline local AI** (phone par chalta hai) + **online AI** (free-tier friendly providers),
ek hi chat mein. Target: Redmi 13C 5G (6 GB RAM), par koi bhi arm64 Android 8+ phone.

> **Honest status:** Code poora likha gaya hai, par jab ye badlav likhe gaye tab Kotlin/Gradle compiler available nahi tha, isliye
> **build aur unit tests wahan chalaye nahi gaye**. GitHub Actions workflow pehle unit tests chalata hai, phir APK banata hai
> (tests fail hon to bhi APK banti hai, par run ke summary mein saaf dikhta hai).
> Jab tak Actions green na ho aur APK phone par test na ho, ise "tested" mat samajhna. Phone par asli model test aap
> Models screen ke 🧪 Test button se kar sakte ho.

## Features (kya hai)

| Part | Kya bana | Kahan |
|---|---|---|
| Local offline AI | LiteRT-LM (Google) runtime, `.litertlm` quantized models, ek samay mein ek model, RAM check, idle unload, low-memory unload, **"Ruko" par jawab beech mein rokna** | `LocalLlm.kt`, `LocalModelCatalog.kt`, `ModelFit.kt`, `DeviceInfo.kt` |
| Model manager | Size/RAM/bhasha download se pehle, progress, resume, cancel, update check, remove, custom URL, internal storage, foreground service | `ModelDownloadService.kt` (isme `ModelDownloader` object), `ModelsActivity.kt` |
| Online providers | Gemini, Groq, OpenRouter, Cerebras, Mistral, Custom (OpenAI-compatible / apna backend). **Gemini zaroori nahi.** Sirf configured (key+model) providers hi available | `Providers.kt`, `ProvidersActivity.kt` |
| Online streaming | Online jawab shabd-shabd karke aata hai (seedhi baat-cheet, mushkil sawaal, code, summary). "Ruko" par network call turant kat jata hai | `Providers.kt`, `OnlineStream.kt` |
| Model router | Task ke hisaab se order, 429/quota par agla model, 5xx par retry, key galat par skip, internet jaye to local, cooldown, active model naam | `RouterCore.kt`, `ModelRouter.kt`, `TaskClassifier.kt` |
| Reasoning | Mushkil sawaal par structured jawab (maqsad, jaankari, steps, vikalp+risk, best, nichod), chhupa chain-of-thought nahi | `Prompts.kt` |
| Self-correction | Calculation checker (app khud jaanchta hai), user correction handling, uncertainty rule, "code test nahi kiya" notice, optional double-check | `SelfCheck.kt`, `Brain.kt` |
| Fresh Mind | 🧠 button: summary, zaroori/faltu facts, asli sawaal, galti check, vikalp, behtar jawab, summary save option | `FreshMind.kt` |
| Memory | Encrypted, view/edit/delete, relevant yaadein hi prompt mein, sensitive baatein bina permission save nahi, history archive | `MemoryStore.kt`, `MemoryLogic.kt`, `ChatStore.kt`, `MemoryActivity.kt`, `HistoryActivity.kt` |
| Voice | Mic button se (kabhi chupke se nahi), partial text preview, stop speaking, offline/online voice label, permission flow | `VoiceInput.kt`, `VoiceHelper.kt`, `ChatActivity.kt` |
| Search | Gemini grounding + Tavily/Brave/Wikipedia, sources links, search fail par saaf notice, offline par local-knowledge notice | `SearchService.kt`, `Honesty` in `SelfCheck.kt` |
| Photo / file / PDF | Photo (gallery/camera) online vision model se, text file summary/sawaal, **PDF**: har page ki tasveer online vision model ko bhejkar text nikalna, phir sawaal ka jawab | `Attachments.kt`, `PdfReader.kt`, `ChatActivity.kt` |
| Phone actions | App kholna, alarm/timer, call/SMS/WhatsApp (confirmation ke saath, ab pehle-step ka "Send" bhi poochta hai), settings pages, screen padhna/tap (Accessibility) | `CommandHandler.kt`, `PhoneActions.kt`, `JarvisAccessibilityService.kt`, `Brain.kt` |
| Security | Keys Keystore mein encrypted, kuch hardcode nahi, sirf HTTPS, errors mein key redact, custom backend option, untrusted file/search/PDF text ke liye guard | `ApiKeyStore.kt`, `Net.kt`, `network_security_config.xml` |

## Local models (verified Hugging Face litert-community pages se)

| Model | Size | Note |
|---|---|---|
| Qwen3 0.6B | ~586 MB | sabse halka |
| Qwen2.5 1.5B (int8) | ~1.6 GB | balanced |
| Qwen2.5 Coder 1.5B (int4) | ~1.1 GB | coding |
| Gemma 4 E2B | ~2.6 GB | best quality, 6 GB par tight |
| Gemma 4 E4B | ~3.65 GB | **experimental**, 6 GB par guarantee nahi. File ka naam pattern se liya, verify nahi hua |

RAM ke aankde andaaze hain. Hindi quality chhote models mein limited hai; Hinglish aksar behtar. Gated models (jaise Gemma 3) ke liye Models screen mein HuggingFace token daalo.

## Build

GitHub par push karo → Actions → **Build APK** → artifacts:

- `Jarvis-APK`: debug APK
- `Test-Report`: unit tests ki report (tests chalne par hamesha upload hoti hai)

Run ke summary mein "Unit tests: PASS / FAIL" likha aata hai.

Local: Android Studio (JDK 17, SDK 35) mein kholo, ya `gradle testDebugUnitTest assembleDebug`
(Gradle 8.7+; repo mein wrapper nahi hai, Android Studio ya `gradle wrapper` se bana lo).
LiteRT-LM version `app/build.gradle.kts` ke `litertLmVersion` mein hai (abhi 0.16.1, ye verify nahi hua; agar "could not resolve" aaye to naya version daalo).
`kotlinx-coroutines-core` dependency isliye rakhi hai kyunki LiteRT-LM ka Kotlin API Flow use karta hai.

## Tests

JVM unit tests (`app/src/test`): calculation checker, think-stripper, sensitive filter, memory ranker, task classifier, context trimmer,
route planner, router core (retry/fallback/cooldown), API parsers, streaming parsers (`OnlineStreamTest`), model-fit.
Phone par chalne wale (instrumented) tests: **pending**.

## PDF padhna (kaise chalta hai)

- Chat → 📎 → "Text ya PDF file". PDF chunne par pehle 8 pages hi padhe jate hain (free limits aur time ke liye).
- Har page ki tasveer online vision model (jaise Gemini) ko bheji jati hai, isliye **internet aur online vision model chahiye** aur PDF ke pages online AI ko jaate hain.
- Har page ek AI call hai, to free limit jaldi khatam ho sakti hai. Beech mein limit aaye to jitne pages padh liye unhi par jawab milta hai (note ke saath).
- Password wali PDF nahi khulti. Local model abhi PDF nahi padhta.

## Pending / jo abhi nahi hai (imaandaari se)

- Build + tests abhi tak chalaye nahi gaye (upar dekho). Gradle wrapper repo mein nahi hai.
- Local model se photo samajhna (abhi sirf online vision model).
- Gemini search wale sawaal (sources ke saath) abhi ek saath poora aate hain, streaming nahi. Phone-kaam, photo/screen dekhna aur PDF ke page padhna bhi streaming nahi karte (jaan-boojhkar).
- Local model ko "Ruko" par rokne ke liye library ka cancel function naam se dhundha jata hai (`cancelProcess` / `cancel`, reflection se). Phone par test karna baaki hai; na mile to UI ruk jata hai par model peeche thodi der chal sakta hai.
- Code ko phone par chalakar test karna (sandbox nahi hai; isliye app code ko "tested" nahi kehta).
- Wake-word / hamesha background listening: jaan-boojhkar nahi banaya (mic sirf button se).
- GPU backend experimental toggle hai, Mali-G57 par test nahi hua.
- Instrumented/UI tests, certificate pinning.

## Privacy

Chat, memory, keys encrypted (Android Keystore). Screen text AI ko bhejna Settings → Privacy se band kar sakte ho. Settings → "Saara local data mitao".
PDF/photo jodne par unka content online AI provider ko jata hai. Docs: `docs/BACKEND_PROXY.md` (production mein keys server par).

## Repair notes (audit ke baad badlav)

Ye badlav bhi **build/test nahi kiye gaye**. Pehle Actions green karo, phir phone par test karo.

- **Galti sudhaar:** jab user "galat" jaisa kuch bole, Jarvi ko pichla sawaal aur pichla jawab saath diya jata hai (usi ek AI call mein, extra call nahi). Tathya wale sawaal par online search se jaanch hoti hai. (`Brain.kt`, `SelfCheck.kt`, `Prompts.kt`)
- **Yaaddasht:** chhoti yaaddasht mein jawab ab 1500 akshar tak (pehle 500), 40 entries tak. (`Brain.kt`)
- **Awaaz:** pitch/rate ka farak bahut halka kiya (chipmunk jaisa nahi), behtar quality wali awaaz pehle, link/emoji/symbols bolne se hataye, error bolkar nahi sunaya jata. Android TTS mein awaaz ka gender poochhne ka tareeka nahi hai, isliye Awaaz 1-6 mein se test karke chuno. (`VoiceHelper.kt`, `SelfCheck.kt`)
- **Baat-cheet mode (hands-free):** Chat menu → "Baat-cheet mode". Jarvi bolne ke baad apne aap sunti hai (chat screen aur floating Jarvi dono par). Ye "sun → samajh → bol → phir sun" ka chakkar hai, asli real-time voice (beech mein tokna) nahi. (`ChatActivity.kt`, `FloatingService.kt`)
- **Errors:** technical error user ko ya awaaz mein nahi dikhta; developer ke liye `Log` mein (key chhupakar). (`ModelRouter.kt`, `ChatActivity.kt`, `FloatingService.kt`)

## Naye badlav (conversation, tokna, purani chats)

- **Baat-cheet/behes:** `Prompts.kt` mein `DIALOGUE_RULES`: pareshani par pehle poochhna, adhoori baat par sawaal, tathya/raay/andaaza mein farak, bina wajah haan-mein-haan nahi, behtar tark ho to maan lena. Koi hardcoded jawab nahi, sirf instructions.
- **Naya message jawab ke beech mein:** purana jawab roka jata hai (aur bola nahi jata), naya message uske baad jata hai. Online streaming wale aur local model ke jawab turant ruk jate hain; bina-streaming wale network calls (jaise phone-kaam) khatam hokar discard hote hain.
- **Beech mein tokna (EXPERIMENTAL, default OFF):** Chat menu → "Beech mein tokna". Jarvi bol rahi ho aur aap bolo to bolna ruk kar sunna shuru. Ye sirf awaaz ka zor naapta hai (`BargeIn.kt`), asli full-duplex nahi. Earphones ke saath behtar; speaker par galat trigger ho sakta hai. Phone par test nahi hua.
- **Purani chats se yaad:** `PastChats.kt` archive ki hui chats mein se milti-julti 1-2 baatein prompt mein jodta hai (sirf phone mein, sensitive nahi, Memory band ho to nahi).

## Taaza batch badlav (is review ke baad)

- **Bhejne se pehle poochna:** SMS/WhatsApp/call ke saath ab "Send" button dabane se pehle bhi (pehle step mein bhi) "Haan ya Nahi" poocha jata hai. (`Brain.kt`)
- **Build workflow:** pehle unit tests (fail hone par bhi APK banti hai, par summary mein FAIL dikhta hai), phir APK. Artifacts: `Jarvis-APK`, `Test-Report`. (`.github/workflows/build.yml`)
- **Local model ko rokna:** jawab ke dauran har 0.2 second par "Ruko" check hota hai. (`LocalLlm.kt`, `ModelRouter.kt`)
- **PDF padhna:** upar dekho. (`PdfReader.kt`, `Attachments.kt`, `ChatActivity.kt`)
- **Online streaming:** shabd-shabd jawab, "Ruko" par network call cancel. (`Providers.kt`, `OnlineStream.kt`)
