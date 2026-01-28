# API Specification for ShieldCall VN Backend

**Base URL (Android Emulator):** `http://10.0.2.2:3000`
**Base URL (Localhost):** `http://localhost:3000`

---

## 1. Check Phone Number
Used when an incoming call is detected or manually queried.

- **Method:** `GET`
- **Endpoint:** `/check-phone`
- **Query Parameters:**
    - `phone` (string): The phone number to check (e.g., `0912345678`).

**Response (JSON):**
```json
{
  "risk_level": "RED",  // Options: "GREEN", "YELLOW", "RED", "SAFE"
  "risk_label": "Cảnh báo: Giả mạo cơ quan chức năng",
  "recommendations": [
    "Tuyệt đối không nghe máy",
    "Không cung cấp mã OTP"
  ]
}
```

---

## 2. Chat with AI Assistant
Used for the floating chat window.

- **Method:** `POST`
- **Endpoint:** `/chat-ai`
- **Content-Type:** `application/json`
- **Body:**
```json
{
  "user_message": "Tin nhắn này có phải lừa đảo không: 'Chuc mung ban trung thuong SH'?",
  "context": "general" // Optional: "sms", "zalo", etc.
}
```

**Response (JSON):**
```json
{
  "ai_response": "Đây là tin nhắn lừa đảo. Không có chương trình nào trao giải qua tin nhắn như vậy.",
  "action_suggested": "BLOCK" // Optional
}
```

---

## 3. Analyze Audio (Call Recording)
Used when the user records a call to check for scam content.

- **Method:** `POST`
- **Endpoint:** `/analyze-audio`
- **Content-Type:** `multipart/form-data`
- **Form Data:**
    - `audio`: File (binary, e.g., `.mp3`, `.m4a`, `.wav`).
    - `phone_number`: String (The phone number being recorded, can be "Unknown").

**Response (JSON):**
```json
{
  "risk_score": 95, // 0-100
  "is_scam": true,
  "transcript": "Tôi là cán bộ điều tra, yêu cầu chị chuyển tiền...",
  "warning_message": "Phát hiện mạo danh công an."
}
```

---

## 4. Analyze Image (Screen Capture)
Used when the user takes a screenshot for OCR and risk analysis.

- **Method:** `POST`
- **Endpoint:** `/analyze-image`
- **Content-Type:** `multipart/form-data`
- **Form Data:**
    - `image`: File (binary, e.g., `.jpg`, `.png`).

**Response (JSON):**
```json
{
  "ocr_text": "Số tài khoản: 190333... Ngân hàng MB...",
  "risk_analysis": {
    "is_safe": false,
    "risk_level": "YELLOW",
    "details": "Phát hiện thông tin chuyển khoản đáng ngờ."
  }
}
```
