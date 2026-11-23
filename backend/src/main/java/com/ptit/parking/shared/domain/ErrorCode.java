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
    PASSWORD_CHANGE_REQUIRED(403, "Bạn cần đổi mật khẩu tạm trước khi tiếp tục."),
    SELF_PROCESSING_FORBIDDEN(403, "Người tạo yêu cầu không được tự xử lý yêu cầu của mình."),

    // 404 — không tìm thấy
    NOT_FOUND(404, "Không tìm thấy tài nguyên."),
    PLATE_NOT_FOUND(404, "Biển số chưa được đăng ký."),
    NO_OPEN_SESSION(404, "Xe không có lượt gửi đang mở."),
    STUDENT_NOT_FOUND(404, "MSSV không tồn tại"),
    GUARD_NOT_FOUND(404, "Không tìm thấy tài khoản bảo vệ."),
    VEHICLE_NOT_FOUND(404, "Không tìm thấy xe."),
    INCIDENT_NOT_FOUND(404, "Không tìm thấy sự cố."),
    WITHDRAWAL_NOT_FOUND(404, "Không tìm thấy yêu cầu rút điểm."),
    TRANSACTION_NOT_FOUND(404, "Không tìm thấy giao dịch gốc."),
    COMPLAINT_NOT_FOUND(404, "Không tìm thấy khiếu nại."),
    PARKING_SESSION_NOT_FOUND(404, "Không tìm thấy lượt gửi xe này trong lịch sử của bạn."),
    SHIFT_ASSIGNMENT_NOT_FOUND(404, "Không tìm thấy ca đã xếp."),

    // 409 — xung đột dữ liệu
    STUDENT_CODE_EXISTS(409, "MSSV đã tồn tại"),
    USERNAME_EXISTS(409, "Tên đăng nhập đã tồn tại"),
    PLATE_EXISTS(409, "Biển số đã được đăng ký"),
    SESSION_ALREADY_OPEN(409, "Xe đang có lượt gửi chưa kết thúc. Không cho xe vào."),
    SESSION_ALREADY_PAID(409, "Lượt gửi này đã được thanh toán. Không trừ điểm lần hai."),
    CONCURRENT_UPDATE(409, "Hệ thống đang bận. Vui lòng thử lại."),
    EMAIL_EXISTS(409, "Email đã được dùng cho tài khoản khác"),
    VEHICLE_PARKED(409, "Xe đang trong bãi. Lấy xe ra trước khi sửa hoặc ngừng sử dụng."),
    VEHICLE_INACTIVE(409, "Xe đã ngừng sử dụng."),
    SHIFT_ALREADY_OPEN(409, "Bạn đang trong một ca trực chưa kết thúc."),
    NO_OPEN_SHIFT(409, "Bạn chưa nhận ca trực."),
    HANDOVER_PENDING(409, "Có biên bản bàn giao ca chưa được xác nhận. Xem lại rồi bấm Nhận ca."),
    INCIDENT_STATUS_CONFLICT(409, "Sự cố không ở trạng thái cho phép thao tác này."),
    WITHDRAWAL_STATUS_CONFLICT(409, "Yêu cầu rút điểm không ở trạng thái cho phép thao tác này."),
    COMPLAINT_STATUS_CONFLICT(409, "Khiếu nại không ở trạng thái cho phép thao tác này."),
    SHIFT_ALREADY_ASSIGNED(409, "Bảo vệ này đã được xếp vào ca đó."),
    PASS_ALREADY_EXISTS(409, "Xe này đã có vé tháng cho tháng đó."),
    ALREADY_RECONCILED(409, "Ngày này đã được chốt đối soát. Biên bản đã chốt không sửa được."),

    // 422 — vi phạm quy tắc nghiệp vụ
    INSUFFICIENT_POINTS(422, "Không đủ điểm."),
    INVALID_AMOUNT(422, "Số tiền hoặc số điểm không hợp lệ."),
    IDEMPOTENCY_KEY_REUSED(422, "Khóa chống xử lý trùng đã được dùng cho yêu cầu khác."),
    PASSWORD_POLICY(422, "Mật khẩu phải dài 8–72 ký tự."),
    PASSWORD_REUSED(422, "Mật khẩu mới phải khác mật khẩu hiện tại."),
    CURRENT_PASSWORD_INCORRECT(422, "Mật khẩu hiện tại không đúng."),
    RESET_CODE_INVALID(422, "Mã xác nhận không đúng."),
    RESET_CODE_EXPIRED(422, "Mã xác nhận đã hết hạn hoặc đã được dùng. Vui lòng yêu cầu mã mới."),
    RESET_CODE_LOCKED(422, "Nhập sai mã quá 5 lần. Vui lòng yêu cầu mã mới."),
    PLATE_REQUIRED(422, "Vui lòng nhập biển số. Chỉ xe đạp không có biển mới được bãi cấp mã."),
    VEHICLE_LIMIT_REACHED(422, "Mỗi sinh viên được tối đa 3 xe đang sử dụng."),
    PHOTO_INVALID(422, "Ảnh phải là JPG, PNG hoặc WEBP và không quá 2 MB."),
    WITHDRAWAL_EXCEEDS_AVAILABLE(422, "Số điểm yêu cầu vượt số điểm còn có thể rút (đã trừ các yêu cầu đang chờ)."),
    CASH_FUND_INSUFFICIENT(422, "Quỹ tiền mặt hiện không đủ để trả yêu cầu này."),
    RECONCILIATION_NOTE_REQUIRED(422, "Tiền đếm được lệch với sổ sách: bắt buộc ghi giải trình."),
    RECONCILIATION_DATE_INVALID(422, "Chỉ đối soát được ngày hôm nay hoặc ngày đã qua."),
    COMPLAINT_NOT_COMPENSABLE(422, "Chỉ khiếu nại xe hư hỏng hoặc mất đồ mới được đề xuất bồi thường."),
    REFUND_NOT_ALLOWED(422, "Chỉ hoàn phí cho khiếu nại thu phí sai có lượt gửi đã thanh toán."),
    REFUND_EXCEEDS_FEE(422, "Số điểm hoàn vượt quá phí đã thu của lượt gửi."),
    TOO_MANY_OPEN_COMPLAINTS(422, "Bạn đang có 5 khiếu nại chưa xử lý xong. Vui lòng chờ kết quả trước khi gửi thêm."),
    SHIFT_SLOT_PASSED(422, "Ca này đã bắt đầu hoặc đã kết thúc: không xếp / gỡ được nữa."),
    GUARD_INACTIVE(422, "Bảo vệ đang bị khóa tài khoản, không xếp ca được."),
    PASS_MONTH_INVALID(422, "Chỉ mua được vé cho tháng này hoặc tháng sau."),
    PASS_NOT_AVAILABLE(422, "Loại xe này chưa bán vé tháng."),
    DATE_RANGE_INVALID(422, "Khoảng ngày không hợp lệ: ngày bắt đầu phải trước ngày kết thúc, tối đa 93 ngày."),
    CHECKOUT_CURFEW(422, "Đã quá giờ khóa lấy xe trong ngày. Lượt gửi giữ nguyên ĐANG GỬI và tính phí qua đêm vào hôm sau."),

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
