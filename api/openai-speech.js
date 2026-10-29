const RATE_WINDOW_MS = 10 * 60 * 1000;
const RATE_MAX_REQUESTS = 20;
const requestBuckets = new Map();

module.exports = async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Use POST /api/openai-speech." });

  const contentLength = Number(req.headers?.["content-length"] || 0);
  if (contentLength > 12000) return res.status(413).json({ error: "Yêu cầu giọng nói quá lớn." });

  const now = Date.now();
  const ip = String(req.headers?.["x-forwarded-for"] || req.socket?.remoteAddress || "unknown").split(",")[0].trim();
  let bucket = requestBuckets.get(ip);
  if (!bucket || now - bucket.startedAt >= RATE_WINDOW_MS) {
    bucket = { startedAt: now, count: 0 };
    requestBuckets.set(ip, bucket);
  }
  bucket.count += 1;
  if (bucket.count > RATE_MAX_REQUESTS) {
    res.setHeader("Retry-After", String(Math.ceil((RATE_WINDOW_MS - (now - bucket.startedAt)) / 1000)));
    return res.status(429).json({ error: "Bạn gửi quá nhiều yêu cầu giọng nói. Hãy thử lại sau." });
  }
  if (requestBuckets.size > 2000) {
    for (const [key, value] of requestBuckets) {
      if (now - value.startedAt >= RATE_WINDOW_MS) requestBuckets.delete(key);
    }
  }

  const apiKey = process.env.OPENAI_API_KEY;
  if (!apiKey) return res.status(503).json({ error: "Máy chủ chưa cấu hình OPENAI_API_KEY." });

  try {
    const body = typeof req.body === "string" ? JSON.parse(req.body) : (req.body || {});
    if (!body || typeof body !== "object" || Array.isArray(body)) return res.status(400).json({ error: "Dữ liệu yêu cầu không hợp lệ." });
    const input = typeof body.input === "string" ? body.input.trim() : "";
    if (!input) return res.status(400).json({ error: "Thiếu nội dung cần đọc." });
    if (input.length > 4000) return res.status(413).json({ error: "Nội dung đọc tối đa 4.000 ký tự." });

    const upstream = await fetch("https://api.openai.com/v1/audio/speech", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Authorization": "Bearer " + apiKey },
      body: JSON.stringify({
        model: (process.env.OPENAI_TTS_MODEL || "gpt-4o-mini-tts").trim(),
        voice: (process.env.OPENAI_TTS_VOICE || "coral").trim(),
        input,
        instructions: "Speak naturally and warmly in Vietnamese, like a helpful conversational AI assistant. Use clear pronunciation, a friendly calm tone, natural pauses, and a moderate pace. Do not sound like an advertisement.",
        response_format: "mp3"
      }),
      signal: AbortSignal.timeout(25000)
    });
    if (!upstream.ok) {
      const status = upstream.status === 429 ? 429 : 502;
      return res.status(status).json({ error: "Dịch vụ giọng nói AI chưa thể trả lời. Hãy thử lại hoặc dùng giọng đọc trên thiết bị." });
    }
    const audio = Buffer.from(await upstream.arrayBuffer());
    if (!audio.length || audio.length > 12 * 1024 * 1024) return res.status(502).json({ error: "Âm thanh trả về không hợp lệ." });
    res.setHeader("Content-Type", "audio/mpeg");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Content-Length", String(audio.length));
    return res.status(200).send(audio);
  } catch (_) {
    return res.status(502).json({ error: "Không thể tạo giọng nói AI lúc này." });
  }
};
