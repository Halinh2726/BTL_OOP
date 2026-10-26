package com.ptit.parking.shared.domain;

/**
 * Lỗi khi vi phạm quy tắc nghiệp vụ.
 * Chỉ MỘT lớp exception + ErrorCode, không tạo cây InsufficientPointsException,
 * PlateExistsException...: các lỗi được xử lý giống nhau nên phân cấp sâu là lạm dụng kế thừa.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public DomainException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() { return errorCode; }
}
