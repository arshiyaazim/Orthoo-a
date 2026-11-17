# orthoo-ai (Native Android Edition)

A privacy-first AI assistant and multi-model workspace for Android, built natively with **Kotlin**, **Jetpack Compose**, **Material Design 3**, **Room**, and **DataStore**.

## Key Features

- **Streaming Chat & Tool-Use Agent (`ChatScreen`)**
  - Real-time Server-Sent Events (SSE) streaming with live tokens/sec, Time-To-First-Token (TTFT), and predicted token counters.
  - Collapsible reasoning / thinking disclosure (`<think>` / `reasoning_content`) and per-session Thinking & Reasoning Effort (`Low` / `Medium` / `High`) controls.
  - Rich Markdown rendering with copyable syntax-formatted fenced code blocks (`JetBrains Mono`), prompt editing with branch regeneration, and multimodal image attachments via Android Photo Picker.
  - Built-in Agent Talents: **Calculator**, **Date & Time**, **Sandboxed HTML Preview** (`WebView`), and **Opt-In Internet Search / Page Reader** (`Wikipedia`, `SearXNG`, `Tavily`, `Brave`, `Exa`).
- **Voice Dictation & Hands-Free Voice Mode**
  - On-device `SpeechRecognizer` dictation and `TextToSpeech` message read-aloud with selectable device voices and optional auto-speak.
- **Pals Personas (`PalsScreen` / `PalSheet`)**
  - Assistant & Roleplay personas with `{{mustache}}` variable substitution, custom dual-color themes, talent toggles, default model bindings, greeting text, suggested prompt cards, and JSON export.
- **Models Hub & Remote Servers (`ModelsScreen`)**
  - Curated starter GGUF presets, live Hugging Face GGUF repository search & resumable HTTP Range downloads, local `.gguf` import with binary GGUF header inspection (`GgufReader`), and OpenAI-compatible remote server discovery (`Ollama`, `LM Studio`, `llama-server`, `vLLM`, `OpenAI`, and `Hugging Face Router`).
  - Supports deep links via `orthoo://hub?repo=<repoId>`.
- **Hardware Telemetry & Benchmarks (`BenchmarkScreen`)**
  - Real device CPU (`ARM FP16`, `DotProd`, `I8MM`, `SVE`), OpenGL ES / Adreno OpenCL GPU, RAM, and storage diagnostics alongside repeatable throughput benchmarks.
- **Adaptive Layout & Theme**
  - Edge-to-edge Material 3 UI (`Fraunces` + `Inter` + `JetBrains Mono`) supporting Light, Dark, and System themes, plus `NavigationRail` + persistent sidebar on expanded tablet screens.

## Architecture

- **UI Layer**: Jetpack Compose (`ui/screens/*`, `ui/components/*`, `ui/theme/*`, `ui/navigation/Routes.kt`) with type-safe Navigation Compose.
- **State Layer**: MVVM ViewModels (`ChatViewModel`, `ModelsViewModel`, `PalsViewModel`, `BenchmarkViewModel`, `SettingsViewModel`, `MainViewModel`) backed by `StateFlow`.
- **Data & Services**: Room (`OrthooDatabase`), Jetpack DataStore (`AppPreferences`), OkHttp SSE & REST clients (`OpenAIClient`, `HuggingFaceApi`, `SearchService`), `ModelDownloadManager`, `GgufReader`, `HardwareInfoProvider`, `SpeechManager`, `TalentEngine`, and `AgentRunner`.

## Note on Native `llama.cpp` Runtime

This pure-Kotlin Android build includes full binary GGUF header verification (`GgufReader`) and Hugging Face GGUF downloading, and connects directly to `llama-server`, `Ollama`, `LM Studio`, `vLLM`, `OpenAI`, or `Hugging Face Router` over HTTP/SSE for live token inference. Native C++ `libllama.so` JNI bindings are not bundled in this build.
