package com.ptit.parking.shared.web;

import com.ptit.parking.shared.domain.ErrorCode;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ lỗi ràng buộc CSDL -> mã lỗi API theo TÊN ràng buộc (Đặc tả API mục 5).
 * <p>Ví dụ hai sinh viên đăng ký cùng biển số đồng thời: cả hai đều qua bước kiểm tra,
 * nhưng CSDL chỉ cho một người ghi; người còn lại nhận {@code PLATE_EXISTS} thay vì lỗi 500.
 * <p>MySQL ghi tên ràng buộc trong câu lỗi, ví dụ
 * {@code Duplicate entry '29A12345' for key 'vehicles.uq_vehicles_plate'}.
 */
@Component
public class ConstraintErrorTranslator {

    /** Kết quả dịch: mã lỗi, và tên trường nếu lỗi gắn với một trường cụ thể của request. */
    public record ConstraintError(ErrorCode code, String field) {}

    private static final Map<String, ConstraintError> BY_CONSTRAINT = Map.of(
            "uq_users_username",          new ConstraintError(ErrorCode.USERNAME_EXISTS, null),
            "uq_students_code",           new ConstraintError(ErrorCode.STUDENT_CODE_EXISTS, null),
            "uq_vehicles_plate",          new ConstraintError(ErrorCode.PLATE_EXISTS, null),
            "fk_vehicles_type",           new ConstraintError(ErrorCode.VALIDATION_ERROR, "vehicleTypeCode"),
            "uq_parking_sessions_open",   new ConstraintError(ErrorCode.SESSION_ALREADY_OPEN, null),
            "uq_payment_details_session", new ConstraintError(ErrorCode.SESSION_ALREADY_PAID, null),
            "ck_wallets_balance",         new ConstraintError(ErrorCode.INSUFFICIENT_POINTS, null));

    /** Duyệt chuỗi nguyên nhân của exception, tìm tên ràng buộc đã biết. */
    public Optional<ConstraintError> translate(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            for (Map.Entry<String, ConstraintError> entry : BY_CONSTRAINT.entrySet()) {
                if (message.contains(entry.getKey())) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }
}