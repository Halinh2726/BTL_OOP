package com.ptit.parking.shared.domain;

/**
 * Lỗi khi vi phạm quy tắc nghiệp vụ.
 * Chỉ MỘT lớp exception + ErrorCode, không tạo cây InsufficientPointsException,
 * PlateExistsException...: các lỗi được xử lý giống nhau nên phân cấp sâu là lạm dụng kế thừa.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String customMessage;

    public DomainException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.customMessage = null;
    }

    public DomainException(ErrorCode errorCode, String customMessage) {
        super(customMessage != null && !customMessage.isBlank() ? customMessage : errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.customMessage = (customMessage != null && !customMessage.isBlank()) ? customMessage : null;
    }

    public ErrorCode errorCode() { return errorCode; }

    public String customMessage() { return customMessage; }
}
