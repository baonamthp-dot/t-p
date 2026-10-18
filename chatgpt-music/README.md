# ChatGPT Music AI — Bảo Nam

Thư mục này là bản web độc lập, có chat OpenAI qua backend, đọc câu trả lời bằng giọng nói của trình duyệt và phát tệp âm thanh do người dùng chọn.

## Triển khai trên Vercel
1. Import repository `baonamthp-dot/t-p` vào Vercel, giữ Root Directory ở thư mục gốc.
2. Trong Vercel → Settings → Environment Variables, thêm `OPENAI_API_KEY` với API key MỚI của bạn. Không đưa key vào HTML, APK hay GitHub.
3. Deploy. Mở `https://<domain-cua-ban>/chatgpt-music/`.
4. Dùng API endpoint `/api/openai-chat`.

Nếu endpoint trả lỗi cấu hình, kiểm tra biến môi trường và redeploy. API có thể phát sinh phí riêng theo mức sử dụng OpenAI API. Trước khi chia sẻ công khai rộng rãi, hãy bổ sung xác thực và giới hạn tần suất.

## Dự án Android hiện tại
Ứng dụng Android cũ và backend Gemini vẫn được giữ nguyên; endpoint OpenAI mới là phần bổ sung, không tự thay đổi APK Android hiện tại. Muốn APK Android gọi endpoint mới, cần cập nhật mã Android để dùng URL `/api/openai-chat`, sau đó build lại qua GitHub Actions.
