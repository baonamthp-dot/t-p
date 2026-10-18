# Bảo Nam Bot — Gemini backend

This Worker keeps the Gemini API key on the server. The Android APK must never contain the Gemini key.

## Deploy (Cloudflare Workers)

1. Create a Cloudflare account and install Node.js/npm on a computer.
2. In this folder, run `npx wrangler login`.
3. Set the secret without putting it in Git:
   `npx wrangler secret put GEMINI_API_KEY`
   Paste the key from your Google AI Studio project into the terminal prompt. Do not commit it or send it in chat.
4. Deploy:
   `npx wrangler deploy`
5. Cloudflare prints a URL like `https://bao-nam-bot-gemini.<your-account>.workers.dev`. The Android app expects the full URL ending in `/chat`.

## Connect the APK

In the GitHub repository settings, add a repository **variable** named `GEMINI_BACKEND_URL` (not the API key) with the full Worker URL ending in `/chat`:

Settings → Secrets and variables → Actions → Variables → New repository variable.

Then run the **Build Music App APK** workflow again. The workflow passes that URL to Gradle as a build property. If the variable is empty or the Worker is unavailable, the app falls back to web search and then offline answers.

## Important security notes

- Keep `GEMINI_API_KEY` as a Cloudflare Worker secret only. Never put it in Android code, Gradle files, GitHub variables, or the APK.
- A public mobile app endpoint can be abused. Configure Cloudflare rate limiting for the Worker route, monitor Gemini usage, and set quota/budget alerts where available before sharing the APK widely.
- This endpoint limits each message to 4,000 characters. It does not persist chat history.
- API usage, model availability, and charges depend on the Google AI Studio / Google Cloud project settings.
