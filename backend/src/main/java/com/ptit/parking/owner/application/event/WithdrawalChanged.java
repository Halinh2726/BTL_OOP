package com.ptit.parking.owner.application.event;

import com.ptit.parking.owner.domain.WithdrawalRequest;
import com.ptit.parking.owner.domain.WithdrawalStatus;
import com.ptit.parking.shared.application.event.NotifiableEvent;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Đợt 5 — yêu cầu rút điểm vừa đổi trạng thái: báo cho bên còn lại (chủ bãi ↔ quản lý). */
public record WithdrawalChanged(long requestId, long ownerId, WithdrawalStatus status, long points, long cashAmount,
                                String note) implements NotifiableEvent {

    private static final String MANAGERS = "QUAN_LY";

    public static WithdrawalChanged of(WithdrawalRequest r) {
        String note = switch (r.status()) {
            case TU_CHOI -> r.rejectReason();
            case KHIEU_NAI -> r.complaintNote();
            case DA_XAC_NHAN -> r.resolutionNote();
            case CHO_XU_LY, DA_TRA, DA_HUY -> null;
        };
        return new WithdrawalChanged(r.id(), r.ownerId(), r.status(), r.points(), r.cashAmount(), note);
    }

    /** switch trên enum: thêm trạng thái mới mà quên quyết định có báo hay không thì trình biên dịch báo lỗi. */
    @Override
    public List<Notice> notices() {
        NumberFormat n = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        String id = "#" + requestId;
        return switch (status) {
            case CHO_XU_LY -> List.of(Notice.toRole(MANAGERS, "RUT_DIEM", "Yêu cầu rút điểm mới " + id,
                    "Chủ bãi xe yêu cầu rút " + n.format(points) + " điểm (" + n.format(cashAmount) + " đ tiền mặt)."));
            case DA_TRA -> List.of(Notice.toUser(ownerId, "RUT_DIEM", "Đã trả tiền yêu cầu " + id,
                    "Quản lý đã trả " + n.format(cashAmount) + " đ. Vui lòng kiểm tra và bấm \"Đã nhận đủ\"."));
            case TU_CHOI -> List.of(Notice.toUser(ownerId, "RUT_DIEM", "Yêu cầu rút điểm " + id + " bị từ chối",
                    "Lý do: " + note));
            case DA_HUY -> List.of(Notice.toRole(MANAGERS, "RUT_DIEM", "Chủ bãi đã hủy yêu cầu " + id,
                    "Yêu cầu rút " + n.format(points) + " điểm không cần xử lý nữa."));
            case KHIEU_NAI -> List.of(Notice.toRole(MANAGERS, "RUT_DIEM", "Chủ bãi báo chưa nhận đủ tiền " + id,
                    "Nội dung: " + note));
            // Chủ bãi tự xác nhận: không cần báo ai; quản lý giải quyết khiếu nại: báo chủ bãi
            case DA_XAC_NHAN -> note == null ? List.of()
                    : List.of(Notice.toUser(ownerId, "RUT_DIEM", "Khiếu nại yêu cầu " + id + " đã được giải quyết",
                    "Quản lý trả lời: " + note));
        };
    }
}
