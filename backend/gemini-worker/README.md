# Gemini Music AI — triển khai không cần Cloudflare

Repository đã có API backend dành cho Vercel tại `api/chat.js`. API gọi Gemini ở phía máy chủ để khóa API không nằm trong APK.

## 1. Chuẩn bị Gemini API key

Tạo key trong [Google AI Studio](https://aistudio.google.com/apikey). Giữ key riêng tư; không gửi key vào chat, không commit lên GitHub và không đưa key vào ứng dụng Android.

## 2. Triển khai API bằng Vercel

1. Mở [Vercel](https://vercel.com/) và đăng nhập. Nếu có lựa chọn, bạn có thể dùng tài khoản GitHub để kết nối repository.
2. Chọn **Add New → Project**, rồi import repository `baonamthp-dot/t-p`.
3. Giữ **Root Directory** ở thư mục gốc của repository. Không chọn `music-app`, vì API nằm trong `api/chat.js`.
4. Trước khi deploy, mở **Environment Variables** và thêm:
   - **Name:** `GEMINI_API_KEY`
   - **Value:** dán Gemini API key của bạn
   - Chọn môi trường **Production** (có thể chọn Preview nếu muốn thử bản xem trước).
5. Nhấn **Deploy** và chờ Vercel hoàn tất.
6. Sau khi triển khai thành công, lấy domain dự án, ví dụ `https://ten-du-an.vercel.app`. Endpoint chat sẽ là `https://ten-du-an.vercel.app/api/chat`.

Không đưa giá trị của `GEMINI_API_KEY` vào GitHub Variables, mã Android, file Gradle hoặc APK. Nếu lỡ công khai key, hãy thu hồi key đó và tạo key mới.

## 3. Kết nối APK qua GitHub

Trong repository GitHub, mở:

**Settings → Secrets and variables → Actions → Variables → New repository variable**

Tạo biến:
- **Name:** `GEMINI_BACKEND_URL`
- **Value:** URL endpoint Vercel đầy đủ, kết thúc bằng `/api/chat` (đây là URL công khai, không phải API key).

Sau đó vào **Actions → Build Music App APK → Run workflow** để build APK mới. Workflow hiện có đọc biến `GEMINI_BACKEND_URL` và đưa URL vào bản build.

## 4. Kiểm tra và xử lý lỗi

- Nếu ứng dụng báo AI chưa được cấu hình: kiểm tra biến `GEMINI_API_KEY` trong Vercel và redeploy.
- Nếu ứng dụng không kết nối được: kiểm tra `GEMINI_BACKEND_URL` có đúng domain và kết thúc bằng `/api/chat` không.
- Nếu Gemini trả lỗi hạn mức: chờ rồi thử lại, đồng thời kiểm tra quota của dự án Google.
- Nếu thay đổi Environment Variables trên Vercel, hãy redeploy để áp dụng.
- Backend giới hạn tin nhắn 4.000 ký tự và không lưu lịch sử hội thoại. Endpoint hiện cho phép gọi công khai; trước khi chia sẻ APK rộng rãi, nên bổ sung xác thực và giới hạn tần suất để ngăn lạm dụng.

## Tính năng của Gemini Music AI

Trợ lý được hướng dẫn hỗ trợ âm nhạc, gợi ý playlist, giải thích BPM/nhạc cụ, học tập và lập trình. Nó không tự phát nhạc hay tìm kiếm web trực tiếp trừ khi ứng dụng được tích hợp công cụ tương ứng.

Cloudflare Worker cũ vẫn nằm trong `backend/gemini-worker/`, nhưng bạn không cần triển khai nó khi sử dụng cấu hình Vercel này.
