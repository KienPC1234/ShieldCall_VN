# Changelog

Tất cả các thay đổi quan trọng của dự án sẽ được ghi lại trong file này.

## [0.2.0] - 2023-10-28

### ✨ Features (Tính năng mới)
- **Settings Screen**: Thêm màn hình Cài đặt, cho phép người dùng:
    - Bật/tắt tính năng bảo vệ cuộc gọi.
    - Mở màn hình quản lý quyền.
    - Gửi email góp ý.
    - Xem phiên bản ứng dụng.
- **UI Update**: Thêm `Toolbar` vào `MainActivity` và nút Cài đặt để truy cập màn hình mới.

### ♻️ Refactoring (Tái cấu trúc)
- `CallReceiver` giờ sẽ kiểm tra cài đặt của người dùng trước khi hiển thị cảnh báo.
- Loại bỏ `FloatingActionButton` debug khỏi `MainActivity`.

## [0.1.0] - 2023-10-27

### ✨ Features (Tính năng mới)
- **Core Calling Feature**: Xây dựng luồng xử lý cốt lõi:
    - Tạo `PermissionActivity` để yêu cầu các quyền cần thiết khi khởi động.
    - Tạo `CallReceiver` để phát hiện cuộc gọi đến.
    - Tạo `OverlayService` để vẽ lớp phủ cảnh báo lên màn hình.
- **API Integration**: Tích hợp Retrofit để gọi API backend, phân tích số điện thoại và hiển thị kết quả.

### 🐛 Bug Fixes (Sửa lỗi)
- Sửa các lỗi crash liên quan đến `DebugController` và ID không tồn tại trong `MainActivity`.
- Khắc phục nhiều lỗi build trong file `build.gradle.kts`.

### ♻️ Refactoring (Tái cấu trúc)
- Hợp nhất `CallOverlayService` và `OverlayService` thành một service duy nhất để đơn giản hóa kiến trúc.
- Cấu trúc lại các file liên quan đến networking vào package `com.sentinel.antiscamvn.network`.
