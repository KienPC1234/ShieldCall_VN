# ShieldCall VN API Documentation

Tài liệu này mô tả các API cần thiết để hỗ trợ ứng dụng ShieldCall VN. Tất cả các endpoint mặc định sử dụng JSON cho dữ liệu gửi đi (trừ Multipart) và trả về.

## Base URL
`https://api.shieldcall.vn/v1/`

---

## 1. Quản lý Phiên (Session)

### Kiểm tra phiên làm việc
**GET** `/check-session`

*   **Query Params:** `session_id` (String)
*   **Response:**
    ```json
    {
      "is_valid": true,
      "new_session_id": null
    }
    ```
    *(Nếu `is_valid` là false, `new_session_id` sẽ chứa ID mới để client cập nhật)*

---

## 2. Kiểm tra Số điện thoại

### Tra cứu mức độ rủi ro
**GET** `/check-phone`

*   **Query Params:** `phone` (String)
*   **Response:**
    ```json
    {
      "risk_level": "DANGER", 
      "risk_label": "Số điện thoại lừa đảo đã được báo cáo",
      "recommendations": ["Chặn số này", "Báo cáo lên hệ thống"]
    }
    ```
    *(risk_level: SAFE, WARNING, DANGER)*

---

## 3. Trợ lý AI (Chatbot)

### Gửi tin nhắn (Normal)
**POST** `/chat-ai`

*   **Body:**
    ```json
    {
      "user_message": "Số 0901234567 có an toàn không?",
      "session_id": "uuid-v4",
      "context": "general"
    }
    ```
*   **Response:**
    ```json
    {
      "ai_response": "Số này đã bị báo cáo là spam nhiều lần.",
      "action_suggested": "block_number"
    }
    ```

### Gửi tin nhắn (Streaming)
**POST** `/chat-ai-stream`

*   **Body:** Giống `/chat-ai`
*   **Response:** `text/event-stream` hoặc chunked transfer. Trả về từng đoạn văn bản cho đến khi kết thúc.

---

## 4. Phân tích Nội dung (Media)

### Phân tích File Âm thanh (Ghi âm cuộc gọi)
**POST** `/analyze-audio`

*   **Content-Type:** `multipart/form-data`
*   **Parts:**
    *   `audio`: File âm thanh (.mp3, .pcm, .wav, v.v.)
    *   `phone_number`: String (Số điện thoại liên quan, nếu có)
*   **Response:**
    ```json
    {
      "risk_score": 85,
      "is_scam": true,
      "transcript": "Chào bạn, tôi gọi từ công an quận...",
      "warning_message": "Phát hiện dấu hiệu giả danh cơ quan chức năng để lừa đảo."
    }
    ```

### Phân tích Hình ảnh (Single/Multiple Screenshots)
**POST** `/analyze-images` (Hoặc `/analyze-image`)

*   **Content-Type:** `multipart/form-data`
*   **Parts:**
    *   `images`: Danh sách các file ảnh (Chụp màn hình)
*   **Response:**
    ```json
    {
      "ocr_text": "Dòng chữ trích xuất từ ảnh...",
      "risk_analysis": {
        "is_safe": false,
        "risk_level": "WARNING",
        "details": "Nội dung chứa liên kết đáng ngờ hoặc yêu cầu chuyển tiền."
      }
    }
    ```

---

## 5. Phân tích Hội thoại (Hành vi)

### Kiểm tra lịch sử tin nhắn/cuộc gọi
**POST** `/analyze-conversation`

*   **Body:**
    ```json
    {
      "phone_number": "0901234567",
      "messages": [
        "Đơn hàng của bạn bị trục trặc...",
        "Vui lòng truy cập https://fake-link.com để cập nhật..."
      ]
    }
    ```
*   **Response:**
    ```json
    {
      "tag": "Lừa đảo trúng thưởng/đơn hàng",
      "risk_level": "DANGER"
    }
    ```

---

## 6. Hệ thống

### Báo cáo lỗi (Crash Report)
**POST** `/report-crash`

*   **Body:**
    ```json
    {
      "device_info": "Samsung SM-G991B (SDK 31)",
      "stack_trace": "java.lang.NullPointerException...",
      "timestamp": 1700000000000
    }
    ```
*   **Response:** `200 OK`

---

## Lưu ý cho Server
1.  **Phân tích Âm thanh:** Server nên sử dụng các mô hình STT (Speech-to-Text) như Whisper và sau đó dùng LLM để phân tích nội dung lừa đảo.
2.  **Streaming:** Endpoint `/chat-ai-stream` cực kỳ quan trọng để tạo trải nghiệm AI mượt mà trên ứng dụng.
3.  **Lưu trữ:** Các file âm thanh/hình ảnh nhận được có thể lưu trữ tạm thời để cải thiện mô hình nhận diện hành vi lừa đảo.
