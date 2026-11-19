package com.ptit.parking.support.adapter.out.persistence;

import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.support.application.port.out.ComplaintRepository;
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

/** Cài đặt {@link ComplaintRepository}: khiếu nại ở complaints, ảnh ở complaint_photos (chỉ đọc khi cần xem). */
@Component
class ComplaintJdbcAdapter implements ComplaintRepository {

    private static final String SELECT = """
            SELECT c.*, p.complaint_id IS NOT NULL AS has_photo
            FROM complaints c
            LEFT JOIN complaint_photos p ON p.complaint_id = c.id
            """;

    private final JdbcClient jdbc;

    ComplaintJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Complaint insert(Complaint c, IncidentPhoto photo) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO complaints (student_id, type, session_id, description, status, created_at)
                VALUES (:student, :type, :session, :description, :status, :createdAt)""")
                .param("student", c.studentId())
                .param("type", c.type().name())
                .param("session", c.sessionId())
                .param("description", c.description())
                .param("status", c.status().name())
                .param("createdAt", utc(c.createdAt()))
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
                       compensation_transaction_id = :compensationTx
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
