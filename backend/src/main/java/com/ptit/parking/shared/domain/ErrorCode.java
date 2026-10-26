package com.ptit.parking.shared.domain;

/**
 * Mã lỗi ổn định trả cho client (Đặc tả API mục 5).
 * Dùng enum thay vì String rải rác: viết sai tên mã là lỗi biên dịch ngay.
 * httpStatus để kiểu int (không dùng HttpStatus của Spring) để domain không phụ thuộc framework.
 */
public enum ErrorCode {

    // 400 — dữ liệu không hợp lệ
    VALIDATION_ERROR(400, "Dữ liệu không hợp lệ."),
    IDEMPOTENCY_KEY_REQUIRED(400, "Thiếu khóa chống xử lý trùng."),

    // 401 / 403 — xác thực, phân quyền
    UNAUTHENTICATED(401, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."),
    INVALID_CREDENTIALS(401, "Sai tên đăng nhập hoặc mật khẩu"),
    ACCOUNT_LOCKED(403, "Tài khoản đã bị khóa. Vui lòng liên hệ quản lý."),
    FORBIDDEN(403, "Bạn không có quyền thực hiện thao tác này."),
    CSRF_INVALID(403, "Phiên làm việc không hợp lệ. Vui lòng tải lại trang."),

    // 404 — không tìm thấy
    NOT_FOUND(404, "Không tìm thấy tài nguyên."),
    PLATE_NOT_FOUND(404, "Biển số chưa được đăng ký."),
    NO_OPEN_SESSION(404, "Xe không có lượt gửi đang mở."),
    STUDENT_NOT_FOUND(404, "MSSV không tồn tại"),
    GUARD_NOT_FOUND(404, "Không tìm thấy tài khoản bảo vệ."),

    // 409 — xung đột dữ liệu
    STUDENT_CODE_EXISTS(409, "MSSV đã tồn tại"),
    USERNAME_EXISTS(409, "Tên đăng nhập đã tồn tại"),
    PLATE_EXISTS(409, "Biển số đã được đăng ký"),
    SESSION_ALREADY_OPEN(409, "Xe đang có lượt gửi chưa kết thúc. Không cho xe vào."),
    SESSION_ALREADY_PAID(409, "Lượt gửi này đã được thanh toán. Không trừ điểm lần hai."),
    CONCURRENT_UPDATE(409, "Hệ thống đang bận. Vui lòng thử lại."),

    // 422 — vi phạm quy tắc nghiệp vụ
    INSUFFICIENT_POINTS(422, "Không đủ điểm."),
    INVALID_AMOUNT(422, "Số tiền hoặc số điểm không hợp lệ."),
    IDEMPOTENCY_KEY_REUSED(422, "Khóa chống xử lý trùng đã được dùng cho yêu cầu khác."),

    // 429 / 500
    TOO_MANY_REQUESTS(429, "Thao tác quá nhiều lần. Vui lòng thử lại sau ít phút."),
    INTERNAL_ERROR(500, "Có lỗi xảy ra. Vui lòng thử lại.");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() { return httpStatus; }

    public String defaultMessage() { return defaultMessage; }
}
