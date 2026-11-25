package com.ptit.parking.shared.presentation.web;

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

    /** Map.ofEntries: không giới hạn 10 cặp như Map.of. */
    private static final Map<String, ConstraintError> BY_CONSTRAINT = Map.ofEntries(
            Map.entry("uq_users_username", new ConstraintError(ErrorCode.USERNAME_EXISTS, null)),
            Map.entry("uq_students_code", new ConstraintError(ErrorCode.STUDENT_CODE_EXISTS, null)),
            Map.entry("uq_vehicles_plate", new ConstraintError(ErrorCode.PLATE_EXISTS, null)),
            Map.entry("uq_vehicles_active_plate", new ConstraintError(ErrorCode.PLATE_EXISTS, null)),
            Map.entry("uq_users_email", new ConstraintError(ErrorCode.EMAIL_EXISTS, null)),
            Map.entry("uq_student_social_identity", new ConstraintError(ErrorCode.SOCIAL_ACCOUNT_EXISTS, null)),
            Map.entry("uq_student_social_provider", new ConstraintError(ErrorCode.SOCIAL_ACCOUNT_EXISTS, null)),
            Map.entry("uq_guard_shifts_open", new ConstraintError(ErrorCode.SHIFT_ALREADY_OPEN, null)),
            Map.entry("uq_withdrawal_requests_transaction", new ConstraintError(ErrorCode.WITHDRAWAL_STATUS_CONFLICT, null)),
            Map.entry("uq_daily_reconciliations_date", new ConstraintError(ErrorCode.ALREADY_RECONCILED, null)),
            Map.entry("uq_shift_assignments", new ConstraintError(ErrorCode.SHIFT_ALREADY_ASSIGNED, null)),
            Map.entry("uq_monthly_passes_vehicle_month", new ConstraintError(ErrorCode.PASS_ALREADY_EXISTS, null)),
            Map.entry("ck_complaints_four_eyes", new ConstraintError(ErrorCode.SELF_PROCESSING_FORBIDDEN, null)),
            Map.entry("uq_complaints_compensation", new ConstraintError(ErrorCode.COMPLAINT_STATUS_CONFLICT, null)),
            Map.entry("uq_complaints_refund", new ConstraintError(ErrorCode.COMPLAINT_STATUS_CONFLICT, null)),
            Map.entry("ck_daily_reconciliations_note", new ConstraintError(ErrorCode.RECONCILIATION_NOTE_REQUIRED, "note")),
            Map.entry("fk_vehicles_type", new ConstraintError(ErrorCode.VALIDATION_ERROR, "vehicleTypeCode")),
            Map.entry("uq_parking_sessions_open", new ConstraintError(ErrorCode.SESSION_ALREADY_OPEN, null)),
            Map.entry("uq_guest_open_plate", new ConstraintError(ErrorCode.GUEST_SESSION_ALREADY_OPEN, null)),
            Map.entry("uq_guest_payment_idempotency", new ConstraintError(ErrorCode.IDEMPOTENCY_KEY_REUSED, null)),
            Map.entry("uq_payment_details_session", new ConstraintError(ErrorCode.SESSION_ALREADY_PAID, null)),
            Map.entry("ck_wallets_balance", new ConstraintError(ErrorCode.INSUFFICIENT_POINTS, null)));

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
