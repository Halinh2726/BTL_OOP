package com.ptit.parking.support.adapter.out.persistence;

import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.support.application.port.out.ComplaintRepository;
import com.ptit.parking.support.domain.CameraReviewStatus;
import com.ptit.parking.support.domain.Complaint;
import com.ptit.parking.support.domain.ComplaintStatus;
import com.ptit.parking.support.domain.ComplaintType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Cài đặt {@link ComplaintRepository}: khiếu nại ở complaints, ảnh ở complaint_photos & complaint_camera_photos. */
@Component
class ComplaintJdbcAdapter implements ComplaintRepository {

    private static final String SELECT = """
            SELECT c.*,
                   p.complaint_id IS NOT NULL AS has_photo,
                   cp.complaint_id IS NOT NULL AS has_camera_photo
            FROM complaints c
            LEFT JOIN complaint_photos p        ON p.complaint_id = c.id
            LEFT JOIN complaint_camera_photos cp ON cp.complaint_id = c.id
            """;

    private final JdbcClient jdbc;

    ComplaintJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Complaint insert(Complaint c, IncidentPhoto photo) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO complaints (student_id, type, session_id, description, status, created_at,
                                        camera_requested, camera_status, incident_area, incident_time_range)
                VALUES (:student, :type, :session, :description, :status, :createdAt,
                        :cameraRequested, :cameraStatus, :area, :timeRange)""")
                .param("student", c.studentId())
                .param("type", c.type().name())
                .param("session", c.sessionId())
                .param("description", c.description())
                .param("status", c.status().name())
                .param("createdAt", utc(c.createdAt()))
                .param("cameraRequested", c.cameraRequested())
                .param("cameraStatus", c.cameraStatus().name())
                .param("area", c.incidentArea())
                .param("timeRange", c.incidentTimeRange())
                .update(keys);
        long id = keys.getKeyAs(Number.class).longValue();
        if (photo != null) {
            jdbc.sql("INSERT INTO complaint_photos (complaint_id, content_type, data) VALUES (:id, :type, :data)")
                    .param("id", id).param("type", photo.contentType()).param("data", photo.data()).update();
        }
        return c.withId(id);
    }

    @Override
    public void update(Complaint c) {
        jdbc.sql("""
                UPDATE complaints SET status = :status, cancelled_at = :cancelledAt, handled_by = :handledBy,
                       handled_at = :handledAt, resolution = :resolution, refund_transaction_id = :refundTx,
                       proposed_points = :proposedPoints, proposed_by = :proposedBy, proposed_at = :proposedAt,
                       decided_by = :decidedBy, decided_at = :decidedAt, decision_note = :decisionNote,
                       compensation_transaction_id = :compensationTx,
                       camera_requested = :cameraRequested, camera_status = :cameraStatus,
                       camera_note = :cameraNote, camera_reviewed_by = :cameraReviewedBy,
                       camera_reviewed_at = :cameraReviewedAt
                WHERE id = :id""")
                .param("status", c.status().name())
                .param("cancelledAt", utc(c.cancelledAt()))
                .param("handledBy", c.handledBy())
                .param("handledAt", utc(c.handledAt()))
                .param("resolution", c.resolution())
                .param("refundTx", c.refundTransactionId())
                .param("proposedPoints", c.proposedPoints())
                .param("proposedBy", c.proposedBy())
                .param("proposedAt", utc(c.proposedAt()))
                .param("decidedBy", c.decidedBy())
                .param("decidedAt", utc(c.decidedAt()))
                .param("decisionNote", c.decisionNote())
                .param("compensationTx", c.compensationTransactionId())
                .param("cameraRequested", c.cameraRequested())
                .param("cameraStatus", c.cameraStatus().name())
                .param("cameraNote", c.cameraNote())
                .param("cameraReviewedBy", c.cameraReviewedBy())
                .param("cameraReviewedAt", utc(c.cameraReviewedAt()))
                .param("id", c.id())
                .update();
    }

    @Override
    public Optional<Complaint> findById(long id) {
        return jdbc.sql(SELECT + "WHERE c.id = :id").param("id", id).query(ComplaintJdbcAdapter::map).optional();
    }

    @Override
    public Optional<Complaint> lockById(long id) {
        return jdbc.sql(SELECT + "WHERE c.id = :id FOR UPDATE OF c").param("id", id)
                .query(ComplaintJdbcAdapter::map).optional();
    }

    @Override
    public List<Complaint> findByStudent(long studentId, int limit) {
        return jdbc.sql(SELECT + "WHERE c.student_id = :student ORDER BY c.id DESC LIMIT :limit")
                .param("student", studentId).param("limit", limit).query(ComplaintJdbcAdapter::map).list();
    }

    @Override
    public List<Complaint> findByStatuses(Collection<ComplaintStatus> statuses, int limit) {
        if (statuses.isEmpty()) {
            return jdbc.sql(SELECT + "ORDER BY c.id DESC LIMIT :limit").param("limit", limit)
                    .query(ComplaintJdbcAdapter::map).list();
        }
        return jdbc.sql(SELECT + "WHERE c.status IN (:statuses) ORDER BY c.id DESC LIMIT :limit")
                .param("statuses", statuses.stream().map(Enum::name).toList())
                .param("limit", limit)
                .query(ComplaintJdbcAdapter::map).list();
    }

    @Override
    public Optional<IncidentPhoto> findPhoto(long complaintId) {
        return jdbc.sql("SELECT content_type, data FROM complaint_photos WHERE complaint_id = :id")
                .param("id", complaintId)
                .query((rs, row) -> new IncidentPhoto(rs.getString("content_type"), rs.getBytes("data")))
                .optional();
    }

    @Override
    public void saveCameraPhoto(long complaintId, IncidentPhoto photo, Instant now) {
        jdbc.sql("""
                INSERT INTO complaint_camera_photos (complaint_id, content_type, data, uploaded_at)
                VALUES (:id, :type, :data, :at)
                ON DUPLICATE KEY UPDATE content_type = VALUES(content_type),
                                        data = VALUES(data),
                                        uploaded_at = VALUES(uploaded_at)""")
                .param("id", complaintId)
                .param("type", photo.contentType())
                .param("data", photo.data())
                .param("at", utc(now))
                .update();
    }

    @Override
    public Optional<IncidentPhoto> findCameraPhoto(long complaintId) {
        return jdbc.sql("SELECT content_type, data FROM complaint_camera_photos WHERE complaint_id = :id")
                .param("id", complaintId)
                .query((rs, row) -> new IncidentPhoto(rs.getString("content_type"), rs.getBytes("data")))
                .optional();
    }

    private static Complaint map(ResultSet rs, int row) throws SQLException {
        return Complaint.restore()
                .id(rs.getLong("id"))
                .studentId(rs.getLong("student_id"))
                .type(ComplaintType.valueOf(rs.getString("type")))
                .sessionId(rs.getObject("session_id", Long.class))
                .description(rs.getString("description"))
                .createdAt(instant(rs, "created_at"))
                .hasPhoto(rs.getBoolean("has_photo"))
                .status(ComplaintStatus.valueOf(rs.getString("status")))
                .cancelledAt(instant(rs, "cancelled_at"))
                .handledBy(rs.getObject("handled_by", Long.class))
                .handledAt(instant(rs, "handled_at"))
                .resolution(rs.getString("resolution"))
                .refundTransactionId(rs.getObject("refund_transaction_id", Long.class))
                .proposedPoints(rs.getObject("proposed_points", Long.class))
                .proposedBy(rs.getObject("proposed_by", Long.class))
                .proposedAt(instant(rs, "proposed_at"))
                .decidedBy(rs.getObject("decided_by", Long.class))
                .decidedAt(instant(rs, "decided_at"))
                .decisionNote(rs.getString("decision_note"))
                .compensationTransactionId(rs.getObject("compensation_transaction_id", Long.class))
                .cameraRequested(rs.getBoolean("camera_requested"))
                .cameraStatus(CameraReviewStatus.valueOf(rs.getString("camera_status")))
                .incidentArea(rs.getString("incident_area"))
                .incidentTimeRange(rs.getString("incident_time_range"))
                .cameraNote(rs.getString("camera_note"))
                .cameraReviewedBy(rs.getObject("camera_reviewed_by", Long.class))
                .cameraReviewedAt(instant(rs, "camera_reviewed_at"))
                .hasCameraPhoto(rs.getBoolean("has_camera_photo"))
                .build();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
