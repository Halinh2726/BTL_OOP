package com.ptit.parking.gate.application.port.out;

import com.ptit.parking.gate.domain.Incident;
import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.gate.domain.IncidentStatus;
import com.ptit.parking.student.domain.PlateNumber;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Port: sự cố (incidents) và ảnh đính kèm (incident_photos). */
public interface IncidentRepository {

    /** Lưu sự cố MỚI kèm ảnh (nếu có) — gọi bên trong transaction để sự cố và ảnh cùng được ghi. */
    Incident save(Incident incident, IncidentPhoto photo);

    Optional<Incident> findById(long incidentId);

    List<Incident> findByReporter(long reporterId, int limit);

    /** Sự cố chưa đóng (MOI, DANG_XU_LY), mới nhất trước. */
    List<Incident> findOpen(int limit);

    long countOpen();

    Optional<IncidentPhoto> findPhoto(long incidentId);

    // ===== Đợt 4b: quản lý xử lý sự cố =====

    /** SELECT ... FOR UPDATE: hai quản lý bấm cùng lúc thì người sau đợi và thấy trạng thái mới. */
    Optional<Incident> lockById(long incidentId);

    /** Ghi trạng thái, người xử lý, thời điểm, kết quả. */
    void updateHandling(Incident incident);

    /** Theo trạng thái; {@code status} null = các sự cố chưa đóng. Mới nhất trước. */
    List<Incident> findByStatus(IncidentStatus status, int limit);

    Map<Long, String> namesOf(Collection<Long> userIds);

    /** ID chủ xe đang hoạt động theo biển số (nếu có) để báo chuông thông báo khi có sự cố liên quan. */
    default Optional<Long> findActiveVehicleOwnerId(PlateNumber plate) {
        return Optional.empty();
    }
}
