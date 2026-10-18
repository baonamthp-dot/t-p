# Bảo Nam Bot — Cấu hình Gemini an toàn

**Không có API key nào được tạo sẵn trong repository này.** Bạn cần tự tạo key trong tài khoản Google AI Studio. Đừng gửi key trong chat, đừng commit key lên GitHub và đừng nhập key vào APK.

## 1. Tạo Gemini API key

Mở [Google AI Studio — API keys](https://aistudio.google.com/apikey), đăng nhập và tạo key cho dự án của bạn. Hãy giữ key riêng tư và theo dõi hạn mức sử dụng.

## 2. Chỗ nhập key (ở máy chủ, không phải ứng dụng)

Trên máy tính, cài Node.js/npm và mở terminal trong thư mục này. Đăng nhập Cloudflare rồi nhập lệnh:

```sh
npx wrangler login
npx wrangler secret put GEMINI_API_KEY
```

Terminal sẽ yêu cầu nhập giá trị secret. **Dán key vào lời nhắc bảo mật của terminal**, không dán vào mã nguồn, GitHub Issues, chat hay tệp cấu hình thường. Đây là nơi lưu key an toàn cho Worker.

Sau đó triển khai Worker:

```sh
npx wrangler deploy
```

Cloudflare sẽ cung cấp URL dạng `https://bao-nam-bot-gemini.<tai-khoan>.workers.dev`. URL ứng dụng cần dùng là URL đó cộng thêm `/chat`.

## 3. Kết nối ứng dụng Android

Trong repository GitHub, mở:

**Settings → Secrets and variables → Actions → Variables → New repository variable**

Tạo biến tên `GEMINI_BACKEND_URL` và đặt giá trị là URL Worker đầy đủ, kết thúc bằng `/chat`. Đây chỉ là URL, **không phải API key**.

Sau đó mở tab Actions và chạy workflow **Build Music App APK**. Workflow sẽ đưa URL máy chủ vào bản build. Nếu URL chưa được cấu hình hoặc Gemini không phản hồi, ứng dụng sẽ thử tìm kiếm web rồi dùng câu trả lời ngoại tuyến.

## Lưu ý bảo mật

- Chỉ lưu `GEMINI_API_KEY` dưới dạng Cloudflare Worker secret.
- Không thêm API key vào APK, mã Android, Gradle, GitHub Variables hoặc commit.
- Trước khi chia sẻ ứng dụng rộng rãi, hãy cấu hình giới hạn tần suất (rate limiting) và theo dõi mức sử dụng/quota để tránh người khác lạm dụng endpoint công khai.
- Worker giới hạn mỗi tin nhắn 4.000 ký tự và không lưu lịch sử hội thoại.
- Hạn mức, tính khả dụng và chi phí phụ thuộc vào cài đặt dự án Google của bạn.
