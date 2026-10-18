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
