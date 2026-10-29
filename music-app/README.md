# ChatGPT Music AI — Bảo Nam

Android Music app written in Kotlin, with an in-app chat assistant and Vietnamese text-to-speech.

## Build APK on GitHub

The workflow **Build Music App APK** runs automatically when files under `music-app/` or its workflow change, and can also be started manually from **Actions → Build Music App APK → Run workflow**.

Download the APK from the workflow run's **Artifacts** section. The artifact is named `MusicApp-debug-apk`.

## Configure the ChatGPT backend

The Android app calls the server endpoint `/api/openai-chat`. It does not contain an OpenAI API key.

1. Deploy this repository to Vercel with the repository root as the project root.
2. In Vercel project settings, add the environment variable `OPENAI_API_KEY` using a newly created key. Never put the key in Android code or commit it to GitHub.
3. Redeploy the Vercel project after adding the variable.
4. In GitHub, open **Settings → Secrets and variables → Actions → Variables** and add a repository variable named `OPENAI_BACKEND_URL` with the base URL of the deployed Vercel site, for example `https://your-project.vercel.app` (replace this example with the actual URL).
5. Run **Actions → Build Music App APK → Run workflow** again, then download `MusicApp-debug-apk`.

If `OPENAI_BACKEND_URL` is empty, the APK will still build but the in-app ChatGPT service will not connect. The server must have `OPENAI_API_KEY` configured for real AI replies. The backend now uses `OPENAI_MODEL` if you set it in Vercel; otherwise it defaults to `gpt-4o-mini`. Choose only a model available to your API project, then redeploy. The upgraded AI prompt improves structured explanations and uncertainty handling, but no AI can guarantee it knows everything. The latest AI Master X update adds stricter step-by-step verification guidance for math, science, and code, clearer uncertainty handling, and a larger response budget. It also improves interpretation of informal Vietnamese, typos, short follow-ups such as “tiếp” or “làm đi”, and context-aware clarification when intent is ambiguous. The prompt now explicitly covers core school subjects across languages, mathematics, sciences, social studies, computing, technology, and arts, with subject-appropriate explanations and checks against the question. The latest update adds cross-disciplinary expert-style response frameworks, adaptive depth (quick answer, step-by-step tutoring, or deeper analysis), and extra caution for high-stakes topics. Conversation understanding is limited to the history sent by the app. To use a stronger model, set `OPENAI_MODEL` in Vercel to a model available to your API project; the default remains `gpt-4o-mini`.


## Music app integration update (v1.7)

- The home-screen **Bảo Nam AI** shortcut opens the full AI chat screen, rather than the local-only command demo.
- Known playback/settings commands are handled locally by the app; general questions use the configured HTTPS `/api/openai-chat` backend.
- The backend API key remains server-side in the `OPENAI_API_KEY` environment variable.
- Build version bumped to 1.7 (versionCode 8). Configure `OPENAI_BACKEND_URL` in GitHub Actions Variables and `OPENAI_API_KEY` on the server before expecting real AI replies.


## Reliability and safety checks (v1.6.1)

- Removed the unused in-app API-key entry screen and stale API-key/model fields; the Android app must not ask for or store an OpenAI key.
- Updated the app information screen to version 1.6 and documented the secure backend path.
- The serverless chat endpoint now rejects oversized requests and applies a best-effort per-instance limit of 20 requests per IP per 10 minutes. For larger production deployments, configure a shared edge rate limiter too.
- The Android GitHub Actions workflow now runs `gradle lintDebug` before building the APK.


## AI and reliability improvements (v1.7)

- The AI backend prefers the OpenAI Responses API with web search enabled when supported, and falls back to Chat Completions when the Responses request is rejected as unsupported.
- Fixed response text formatting so extracted multi-part answers keep real line breaks.
- The speech endpoint now rejects oversized HTTP requests before processing, in addition to limiting the spoken text length and response audio size.
- The app display name is now **Bảo Nam Music**; the AI chat's clear-conversation action also clears the conversation history used for follow-up context.
- Added a backend syntax-check workflow for the JavaScript API endpoints.
- The Android APK workflow must pass lint and build checks before an APK artifact is published. A successful build verifies compilation and packaging, not every runtime feature; the deployed backend still needs `OPENAI_API_KEY`, and the APK needs `OPENAI_BACKEND_URL` configured at build time.
