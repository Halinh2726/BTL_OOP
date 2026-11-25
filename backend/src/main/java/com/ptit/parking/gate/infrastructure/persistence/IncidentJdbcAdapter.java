package com.ptit.parking.gate.infrastructure.persistence;

import com.ptit.parking.gate.application.port.out.IncidentRepository;
import com.ptit.parking.gate.domain.Incident;
import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.gate.domain.IncidentStatus;
import com.ptit.parking.gate.domain.IncidentType;
import com.ptit.parking.student.domain.PlateNumber;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Cài đặt {@link IncidentRepository}: sự cố ở incidents, ảnh ở incident_photos (chỉ đọc khi cần xem ảnh). */
@Component
class IncidentJdbcAdapter implements IncidentRepository {

    private static final String SELECT = """
            SELECT i.id, i.reported_by, i.type, i.plate_number, i.description, i.status, i.created_at,
                   i.handled_by, i.handled_at, i.resolution, p.incident_id IS NOT NULL AS has_photo
            FROM incidents i
            LEFT JOIN incident_photos p ON p.incident_id = i.id
            """;

    private final JdbcClient jdbc;

    IncidentJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Incident save(Incident incident, IncidentPhoto photo) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO incidents (reported_by, type, plate_number, description, status, created_at)
                VALUES (:reporter, :type, :plate, :description, :status, :createdAt)""")
                .param("reporter", incident.reporterId())
                .param("type", incident.type().name())
                .param("plate", incident.plate().map(PlateNumber::value).orElse(null))
                .param("description", incident.description())
                .param("status", incident.status().name())
                .param("createdAt", LocalDateTime.ofInstant(incident.createdAt(), ZoneOffset.UTC))
                .update(keys);
        long id = keys.getKey().longValue();
        if (photo != null) {
            jdbc.sql("INSERT INTO incident_photos (incident_id, content_type, data) VALUES (:id, :type, :data)")
                    .param("id", id)
                    .param("type", photo.contentType())
                    .param("data", photo.data())
                    .update();
        }
        return incident.withId(id);
    }

    @Override
    public Optional<Incident> findById(long incidentId) {
        return jdbc.sql(SELECT + "WHERE i.id = :id").param("id", incidentId).query(IncidentJdbcAdapter::map).optional();
    }

    @Override
    public List<Incident> findByReporter(long reporterId, int limit) {
        return jdbc.sql(SELECT + "WHERE i.reported_by = :reporter ORDER BY i.id DESC LIMIT :limit")
                .param("reporter", reporterId).param("limit", limit).query(IncidentJdbcAdapter::map).list();
    }

    @Override
    public List<Incident> findOpen(int limit) {
        return jdbc.sql(SELECT + "WHERE i.status IN ('MOI', 'DANG_XU_LY') ORDER BY i.id DESC LIMIT :limit")
                .param("limit", limit).query(IncidentJdbcAdapter::map).list();
    }

    @Override
    public long countOpen() {
        return jdbc.sql("SELECT COUNT(*) FROM incidents WHERE status IN ('MOI', 'DANG_XU_LY')")
                .query(Long.class).single();
    }

    @Override
    public Optional<IncidentPhoto> findPhoto(long incidentId) {
        return jdbc.sql("SELECT content_type, data FROM incident_photos WHERE incident_id = :id")
                .param("id", incidentId)
                .query((rs, row) -> new IncidentPhoto(rs.getString("content_type"), rs.getBytes("data")))
                .optional();
    }

    // ===== Đợt 4b: quản lý xử lý sự cố =====

    @Override
    public Optional<Incident> lockById(long incidentId) {
        // Khóa đúng dòng incidents; dòng ảnh (LEFT JOIN) không bị khóa
        return jdbc.sql(SELECT + "WHERE i.id = :id FOR UPDATE OF i").param("id", incidentId)
                .query(IncidentJdbcAdapter::map).optional();
    }

    @Override
    public void updateHandling(Incident incident) {
        jdbc.sql("""
                UPDATE incidents SET status = :status, handled_by = :handledBy, handled_at = :handledAt,
                                     resolution = :resolution
                WHERE id = :id""")
                .param("status", incident.status().name())
                .param("handledBy", incident.handledBy())
                .param("handledAt", incident.handledAt() == null ? null
                        : LocalDateTime.ofInstant(incident.handledAt(), ZoneOffset.UTC))
                .param("resolution", incident.resolution())
                .param("id", incident.id())
                .update();
    }

    @Override
    public List<Incident> findByStatus(IncidentStatus status, int limit) {
        if (status == null) {
            return findOpen(limit);
        }
        return jdbc.sql(SELECT + "WHERE i.status = :status ORDER BY i.id DESC LIMIT :limit")
                .param("status", status.name()).param("limit", limit).query(IncidentJdbcAdapter::map).list();
    }

    @Override
    public Map<Long, String> namesOf(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            jdbc.sql("SELECT id, full_name FROM users WHERE id IN (:ids)").param("ids", List.copyOf(userIds))
                    .query((rs, row) -> names.put(rs.getLong("id"), rs.getString("full_name"))).list();
        }
        return names;
    }

    @Override
    public Optional<Long> findActiveVehicleOwnerId(PlateNumber plate) {
        if (plate == null) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT student_id FROM vehicles WHERE active_plate = :plate")
                .param("plate", plate.value())
                .query(Long.class)
                .optional();
    }

    private static Incident map(ResultSet rs, int row) throws SQLException {
        String plate = rs.getString("plate_number");
        return Incident.restore(rs.getLong("id"), rs.getLong("reported_by"), IncidentType.valueOf(rs.getString("type")),
                plate == null ? null : new PlateNumber(plate), rs.getString("description"),
                instant(rs, "created_at"), rs.getBoolean("has_photo"), IncidentStatus.valueOf(rs.getString("status")),
                rs.getObject("handled_by", Long.class), instant(rs, "handled_at"), rs.getString("resolution"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
