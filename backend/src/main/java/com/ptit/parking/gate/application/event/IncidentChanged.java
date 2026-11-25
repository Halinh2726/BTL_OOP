package com.ptit.parking.gate.application.event;

import com.ptit.parking.gate.domain.Incident;
import com.ptit.parking.gate.domain.IncidentStatus;
import com.ptit.parking.gate.domain.IncidentType;
import com.ptit.parking.shared.application.event.NotifiableEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Sự cố mới được báo hoặc đổi trạng thái xử lý: báo Quản lý có sự cố mới, báo Bảo vệ tiến độ xử lý,
 * và báo chủ xe (nếu sự cố gắn với biển số xe đã đăng ký) để theo dõi.
 */
public record IncidentChanged(
        long incidentId,
        long reporterId,
        IncidentType type,
        String plateDisplay,
        String description,
        IncidentStatus status,
        String resolution,
        Long vehicleOwnerUserId) implements NotifiableEvent {

    public static IncidentChanged of(Incident incident, Long vehicleOwnerUserId) {
        String plate = incident.plate().map(p -> p.display()).orElse(null);
        return new IncidentChanged(
                incident.id(),
                incident.reporterId(),
                incident.type(),
                plate,
                incident.description(),
                incident.status(),
                incident.resolution(),
                vehicleOwnerUserId);
    }

    @Override
    public List<Notice> notices() {
        String id = "#" + incidentId;
        String typeLabel = label(type);
        List<Notice> out = new ArrayList<>();
        switch (status) {
            case MOI -> {
                String detail = (plateDisplay != null ? "Biển số " + plateDisplay + ": " : "") + description;
                out.add(Notice.toRole("QUAN_LY", "SU_CO", "Sự cố mới " + id + " — " + typeLabel, detail));
                if (vehicleOwnerUserId != null && vehicleOwnerUserId != reporterId) {
                    out.add(Notice.toUser(vehicleOwnerUserId, "SU_CO",
                            "Cảnh báo sự cố xe " + plateDisplay + " (" + id + ")",
                            typeLabel + ": " + description));
                }
            }
            case DANG_XU_LY -> out.add(Notice.toUser(reporterId, "SU_CO",
                    "Sự cố " + id + " đang được xử lý",
                    "Quản lý đã tiếp nhận báo cáo sự cố "
                            + (plateDisplay != null ? "xe " + plateDisplay : typeLabel.toLowerCase()) + "."));
            case DA_XU_LY -> {
                out.add(Notice.toUser(reporterId, "SU_CO",
                        "Sự cố " + id + " đã xử lý xong",
                        "Kết quả: " + resolution));
                if (vehicleOwnerUserId != null && vehicleOwnerUserId != reporterId) {
                    out.add(Notice.toUser(vehicleOwnerUserId, "SU_CO",
                            "Sự cố xe " + plateDisplay + " (" + id + ") đã xử lý",
                            "Kết quả: " + resolution));
                }
            }
            case TU_CHOI -> out.add(Notice.toUser(reporterId, "SU_CO",
                    "Báo cáo sự cố " + id + " bị từ chối",
                    "Lý do: " + resolution));
        }
        return out;
    }

    private static String label(IncidentType type) {
        if (type == null) return "Sự cố bãi xe";
        return switch (type) {
            case XE_GUI_QUA_LAU -> "Xe gửi quá lâu";
            case XE_CHUA_DANG_KY -> "Xe chưa đăng ký đỗ trong bãi";
            case XE_HU_HONG -> "Xe bị hư hỏng";
            case KHAC -> "Sự cố khác";
        };
    }
}
