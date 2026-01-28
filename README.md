# ShieldCall VN

Ứng dụng Android giúp bảo vệ người dùng Việt Nam khỏi các cuộc gọi lừa đảo, cung cấp cảnh báo theo thời gian thực và hướng dẫn hành động an toàn.

## Tính năng chính

- **Cảnh báo cuộc gọi lừa đảo**: Hiển thị lớp phủ (overlay) ngay khi có cuộc gọi đến, thông báo mức độ rủi ro của số điện thoại.
- **Phân tích rủi ro**: Sử dụng API backend (do bạn phát triển) để chấm điểm và phân loại kịch bản lừa đảo (giả mạo ngân hàng, công an, giao hàng...).
- **Giao diện đơn giản**: Yêu cầu quyền và hiển thị cảnh báo một cách trực quan, dễ hiểu.
- **Chế độ "Ba/Mẹ" (Sắp có)**: Giao diện tối giản với các nút bấm lớn, dễ sử dụng cho người lớn tuổi.
- **Báo cáo cộng đồng (Sắp có)**: Cho phép người dùng báo cáo các số điện thoại lừa đảo để cập nhật hệ thống.

## Công nghệ sử dụng

- **Frontend**: Android Native (Kotlin)
- **Kiến trúc**: Single Activity, Service-based for background tasks.
- **Networking**: Retrofit & Gson
- **Backend (Cần bạn xây dựng)**: Django/Python

## Cách hoạt động

1.  **Lần đầu cài đặt**: Ứng dụng yêu cầu người dùng cấp quyền `READ_PHONE_STATE` (để phát hiện cuộc gọi) và `SYSTEM_ALERT_WINDOW` (để hiển thị cảnh báo).
2.  **Khi có cuộc gọi đến**: `CallReceiver` bắt sự kiện, lấy số điện thoại và khởi chạy `OverlayService`.
3.  **Hiển thị cảnh báo**: `OverlayService` gọi API backend để kiểm tra số điện thoại, sau đó hiển thị một lớp phủ trên màn hình cuộc gọi với màu sắc (Đỏ/Vàng/Xanh) và nội dung cảnh báo tương ứng.

lỗi khi bấm biểu tượng mic thì app bị thoát