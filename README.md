# ShieldCall VN

Ứng dụng Android giúp bảo vệ người dùng Việt Nam khỏi các cuộc gọi lừa đảo, cung cấp cảnh báo theo thời gian thực và hỗ trợ phân tích nội dung đáng ngờ bằng AI.

## Tính năng chính

- **🛡️ Cảnh báo cuộc gọi lừa đảo**: Hiển thị lớp phủ (overlay) ngay khi có cuộc gọi đến, thông báo mức độ rủi ro (Xanh/Vàng/Đỏ) dựa trên cơ sở dữ liệu số điện thoại.
- **🤖 Chat AI Assistant**: Cửa sổ chat nổi hỗ trợ stream phản hồi thời gian thực, giúp người dùng kiểm tra các nội dung tin nhắn, kịch bản đáng ngờ.
- **📸 Chụp màn hình & Phân tích OCR**: 
    - Chế độ chụp nhiều ảnh (Multi-capture) với nút điều khiển nổi.
    - Tự động ẩn giao diện điều khiển khi chụp để tránh che khuất thông tin.
    - Phân tích văn bản từ ảnh chụp (STK, nội dung tin nhắn) để đưa ra cảnh báo.
- **🎙️ Ghi âm cuộc gọi**: Giao diện thu gọn (chỉ hiện đồng hồ và nút dừng), hỗ trợ ghi âm nội dung cuộc gọi (`VOICE_COMMUNICATION`).
- **📁 Quản lý đính kèm**: Hỗ trợ chọn và preview tối đa 5 ảnh trước khi gửi cho AI, có thể xóa ảnh nhầm bằng nút X.
- **⚡ Quản lý phiên (Session)**: Tự động duy trì và xác thực phiên làm việc với server để đảm bảo ngữ cảnh hội thoại ổn định.
- **🔧 Chế độ Debug & Báo cáo lỗi**: 
    - Lưu bản ghi âm vào thư mục Downloads khi bật Debug.
    - Tự động thu thập và hỏi ý kiến người dùng gửi báo cáo lỗi (Crash Report) cho nhà phát triển.

## Công nghệ sử dụng

- **Frontend**: Android Native (Kotlin)
- **UI Framework**: Material Design 3 (M3)
- **Kiến trúc**: Service-based Overlay, Broadcast Receiver cho hệ thống.
- **Networking**: Retrofit 2, OkHttp (Hỗ trợ Streaming/SSE)
- **Local Storage**: SharedPreferences (với PreferenceManager)
- **Permissions**: Quản lý quyền phức tạp cho Android 14+ (FGS mediaProjection, Microphone, Overlay).

## Cách hoạt động

1.  **Cấp quyền**: Ứng dụng yêu cầu quyền hiển thị trên ứng dụng khác, micro, và ghi màn hình.
2.  **Giám sát**: `CallReceiver` bắt sự kiện cuộc gọi để hiện cảnh báo nhanh.
3.  **Hỗ trợ AI**: Người dùng có thể mở icon nổi bất cứ lúc nào để chat, chụp màn hình hoặc ghi âm nội dung nghi ngờ.
4.  **Backend**: Toàn bộ dữ liệu được gửi về server backend để xử lý OCR, Speech-to-Text và phân tích hành vi qua mô hình AI.
