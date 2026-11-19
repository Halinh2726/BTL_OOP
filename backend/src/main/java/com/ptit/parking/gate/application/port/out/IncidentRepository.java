package com.ptit.parking.gate.application.port.out;

import com.ptit.parking.gate.domain.Incident;
import com.ptit.parking.gate.domain.IncidentPhoto;
import java.util.List;
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
}
