# API_PROVIDER_INVENTORY

Sources: Maya_v5_2_56_API_Deep_Audit.xlsx (sheets "AI Services", "Non-AI APIs", "API Key Config") and Jarvis source (Providers.kt, ProvidersActivity.kt).
A URL or catalog entry in source proves configuration, not that the feature runs. No secret values were found or reported.

## 1. Chat / LLM providers

| Provider | In Maya (per audit) | In Jarvis (Providers.kt) | Note |
|---|---|---|---|
| OpenAI | yes | key-prefix detection listed; native entry not seen in the provider list | OpenAI-compatible, usable through Custom |
| Google Gemini | yes | yes (own adapter) | search is built in |
| Groq | yes | yes | |
| DeepSeek | yes | key-prefix detection listed | OpenAI-compatible, usable through Custom |
| Anthropic Claude | yes (Messages API) | key-prefix detection listed; a native Messages adapter NOT verified | Claude uses its own request format, so Custom (OpenAI-compatible) may not work |
| OpenRouter | yes | yes | |
| Together AI | yes | key-prefix detection listed | OpenAI-compatible |
| Mistral | yes | yes | |
| NVIDIA NIM | yes | key-prefix detection listed | OpenAI-compatible |
| Cerebras | not in the audit | yes | extra in Jarvis |
| Custom / own backend | n/a | yes ("Custom / apna backend") | good fit for the future cloud backend |

## 2. Other AI service types (Maya audit)

| Type | Providers found in Maya | Jarvis status |
|---|---|---|
| Image generation | Google (Imagen / Gemini image), OpenAI GPT Image, xAI grok-2-image, DeepInfra FLUX, Together FLUX, NVIDIA FLUX, Pollinations | not verified in Jarvis; treat as a future feature |
| Image understanding | via vision-capable chat models (Gemini, Claude, OpenAI) | Jarvis marks vision-capable platforms (vision = true) |
| Speech-to-text | Deepgram nova-3 (WebSocket) | not used; Jarvis uses offline Vosk |
| Text-to-speech | Maya Cloud TTS (proprietary), Google Cloud TTS | Maya Cloud TTS: do not use. Jarvis has Android TTS and a Gemini TTS path |
| Live voice | Gemini Live (WebSocket) | not in Jarvis |
| Web search | Tavily, SerpAPI, Brave | Jarvis offers Tavily, Brave, Wikipedia and Gemini built-in |
| Agent / sync | Maya agentic key slot, HunterisLive sync (custom service) | not applicable |

## 3. Non-AI integrations seen in Maya (not AI providers)

Open-Meteo, Google Maps Places, YouTube Data, Yahoo Finance, Google Drive, Todoist, Notion, Linear, GitHub, GitLab, Vercel, Telegram Bot. They are listed for completeness only and are not imported into Jarvis.

## 4. Rules for Jarvis

1. One adapter per endpoint type. Providers that are OpenAI-compatible use the shared adapter; only Gemini (and Claude if added) need their own.
2. API keys stay on the device (Jarvis already seals them with ApiKeyStore) and on the backend; never in the repo, logs or frontend code.
3. Maya's own servers (getmaya.online, api.getmaya.cloud, hunterislive) must not be called by Jarvis.
4. Every provider call goes through ModelRouter and CountingBackend so it is counted on the AI Dashboard.

## 5. Gaps

- Native Anthropic Messages adapter: not verified in Jarvis.
- Image generation: not verified in Jarvis.
- Provider pricing data: not stored; the dashboard uses a user-set rate and labels money as an estimate.
