# Changelog

Tất cả các thay đổi quan trọng của dự án sẽ được ghi lại trong file này.

## [1.0.0] - 2026-01-28

### ✨ Features (Tính năng mới)
- **Manual Multi-Capture**: Chế độ chụp màn hình thủ công cho phép chụp nhiều ảnh liên tiếp.
- **Improved Chat UI**: 
    - Hỗ trợ gửi tin nhắn kèm tối đa 5 ảnh.
    - Xem trước (preview) ảnh với nút X để xóa trước khi gửi.
    - Căn lề tin nhắn ảnh theo hướng người dùng/bot.
    - Hỗ trợ phản hồi dạng Stream (SSE) từ server.
- **Compact Recording UI**: Giao diện ghi âm tối giản với đồng hồ nổi và nút dừng riêng biệt.
- **Session Management**: Tự động xác thực và làm mới phiên chat với backend.
- **Crash Reporting**: Tự động thu thập log và hỏi ý kiến người dùng gửi báo cáo lỗi khi app gặp sự cố.
- **Floating Menu Enhancement**: Thêm nút ẩn bong bóng và khôi phục từ thanh thông báo.

### 🐛 Bug Fixes (Sửa lỗi)
- Sửa lỗi `SecurityException` trên Android 14 khi khởi tạo `mediaProjection` sai thứ tự.
- Sửa lỗi `ClassCastException` trong màn hình Cài đặt do sử dụng sai loại Switch.
- Sửa lỗi `IllegalArgumentException` khi gỡ View chưa được attach vào WindowManager.
- Khắc phục tình trạng bàn phím che mất ô nhập liệu chat (`SOFT_INPUT_ADJUST_RESIZE`).
- Đảm bảo chatbox không đè lên giao diện chọn ảnh của hệ thống.
- **UI Fix**: Ẩn bảng điều khiển chụp màn hình khi đang thực hiện chụp để không che mất thông tin.

### ♻️ Refactoring (Tái cấu trúc)
- Đồng bộ hóa toàn bộ Action Intent sử dụng hằng số trong `OverlayService`.
- Chuyển sang sử dụng `IntentCompat` và các API hiện đại để giảm thiểu cảnh báo "deprecated".
- Tối ưu hóa việc kiểm tra trạng thái mạng trước khi thực hiện request.

## [0.2.0] - 2023-10-28
- Xem lại lịch sử các phiên bản cũ...