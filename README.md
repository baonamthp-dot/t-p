# t-p

## 🎵 Neon Dream

Bản nhạc instrumental ngắn được tạo bằng Python, không cần API hay thư viện ngoài.

### Chạy

```bash
python music.py
```

Sau khi chạy, chương trình tạo file `neon_dream.wav`.


## Gemini Music AI (Android)

Backend triển khai trên Vercel, không cần Cloudflare: xem [hướng dẫn Gemini Music AI](backend/gemini-worker/README.md). API serverless nằm tại [api/chat.js](api/chat.js); cần cấu hình secret `GEMINI_API_KEY` trong Vercel và repository variable `GEMINI_BACKEND_URL` trong GitHub Actions trước khi build APK.
