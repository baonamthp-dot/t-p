export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Content-Type": "application/json; charset=utf-8"
    };
    const json = (status, body) => new Response(JSON.stringify(body), { status, headers: cors });
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (request.method !== "POST") return json(405, { error: "Use POST /chat." });
    if (!env.GEMINI_API_KEY) return json(503, { error: "Server AI is not configured yet." });

    try {
      const length = Number(request.headers.get("content-length") || "0");
      if (length > 12000) return json(413, { error: "Request too large." });
      const body = await request.json();
      const message = typeof body.message === "string" ? body.message.trim() : "";
      if (!message) return json(400, { error: "message is required." });
      if (message.length > 4000) return json(413, { error: "Message is too long (max 4000 characters)." });

      const upstream = await fetch(
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": env.GEMINI_API_KEY
          },
          body: JSON.stringify({
            systemInstruction: {
              parts: [{
                text: "Bạn là Bảo Nam Bot, trợ lý tiếng Việt thân thiện trong ứng dụng Music App. Trả lời trực tiếp, hữu ích, rõ ràng; với bài tập hãy giải thích từng bước; với lập trình hãy nêu giả định và cảnh báo lỗi có thể có. Không bịa nguồn, không khẳng định điều chưa chắc chắn. Nếu thiếu dữ kiện, hãy hỏi lại. Không tuyên bố đã chạy code hay truy cập dữ liệu nếu chưa thực sự làm."
              }]
            },
            contents: [{ role: "user", parts: [{ text: message }] }],
            generationConfig: { temperature: 0.65, maxOutputTokens: 1800 }
          }),
          signal: AbortSignal.timeout(25000)
        }
      );
      const data = await upstream.json();
      if (!upstream.ok) {
        // Never return upstream headers or credentials to the app.
        return json(upstream.status === 429 ? 429 : 502, {
          error: upstream.status === 429
            ? "AI đang bận hoặc đã chạm hạn mức. Hãy thử lại sau."
            : "Gemini tạm thời không trả lời được."
        });
      }
      const answer = (data.candidates || [])
        .flatMap(candidate => candidate.content?.parts || [])
        .map(part => typeof part.text === "string" ? part.text : "")
        .join("\n")
        .trim();
      if (!answer) return json(502, { error: "Gemini returned no text." });
      return json(200, { answer });
    } catch (_) {
      return json(502, { error: "Không thể kết nối Gemini lúc này." });
    }
  }
};
