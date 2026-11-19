package com.ptit.parking.support.application.event;

import com.ptit.parking.shared.application.event.NotifiableEvent;
import com.ptit.parking.support.domain.Complaint;
import com.ptit.parking.support.domain.ComplaintStatus;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Đợt 5 — khiếu nại vừa được gửi hoặc đổi trạng thái: báo sinh viên kết quả, báo quản lý có việc mới, báo chủ bãi
 * có đề xuất bồi thường cần duyệt, báo người đề xuất kết quả duyệt.
 */
public record ComplaintChanged(long complaintId, long studentId, ComplaintStatus status, String resolution,
                               Long proposedPoints, Long proposedBy, String decisionNote) implements NotifiableEvent {

    public static ComplaintChanged of(Complaint c) {
        return new ComplaintChanged(c.id(), c.studentId(), c.status(), c.resolution(), c.proposedPoints(),
                c.proposedBy(), c.decisionNote());
    }

    @Override
    public List<Notice> notices() {
        NumberFormat n = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        String id = "#" + complaintId;
        List<Notice> out = new ArrayList<>();
        switch (status) {
            case MOI -> out.add(Notice.toRole("QUAN_LY", "KHIEU_NAI", "Khiếu nại mới " + id,
                    "Sinh viên vừa gửi khiếu nại, vào mục Xử lý để tiếp nhận."));
            case DANG_XU_LY -> out.add(student("Khiếu nại " + id + " đã được tiếp nhận",
                    "Quản lý đang xem xét khiếu nại của bạn."));
            case DA_XU_LY -> out.add(student("Khiếu nại " + id + " đã xử lý xong", "Kết luận: " + resolution));
            case TU_CHOI -> out.add(student("Khiếu nại " + id + " không được chấp nhận", "Lý do: " + resolution));
            case CHO_DUYET_BOI_THUONG -> {
                out.add(student("Khiếu nại " + id + " đang chờ duyệt bồi thường",
                        "Quản lý đề xuất bồi thường " + n.format(proposedPoints) + " điểm, đang chờ chủ bãi xe duyệt."));
                out.add(Notice.toRole("CHU_TAI_KHOAN", "BOI_THUONG", "Đề xuất bồi thường mới " + id,
                        n.format(proposedPoints) + " điểm — căn cứ: " + resolution));
            }
            case DA_BOI_THUONG -> {
                out.add(student("Bạn được bồi thường " + n.format(proposedPoints) + " điểm",
                        "Khiếu nại " + id + ": điểm đã được cộng vào ví của bạn."));
                out.add(Notice.toUser(proposedBy, "BOI_THUONG", "Chủ bãi đã duyệt bồi thường " + id,
                        decisionNote == null ? "Đã chuyển điểm cho sinh viên." : "Ghi chú: " + decisionNote));
            }
            case KHONG_BOI_THUONG -> {
                out.add(student("Khiếu nại " + id + " không được bồi thường", "Lý do: " + decisionNote));
                out.add(Notice.toUser(proposedBy, "BOI_THUONG", "Chủ bãi không duyệt bồi thường " + id,
                        "Lý do: " + decisionNote));
            }
            case DA_HUY -> { }                                   // sinh viên tự hủy: không cần báo ai
        }
        return out;
    }

    private Notice student(String title, String body) {
        return Notice.toUser(studentId, "KHIEU_NAI", title, body);
    }
}
