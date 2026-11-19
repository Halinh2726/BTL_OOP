package com.ptit.parking.owner.adapter.out.persistence;

import com.ptit.parking.owner.application.port.out.WithdrawalRequestRepository;
import com.ptit.parking.owner.domain.WithdrawalRequest;
import com.ptit.parking.owner.domain.WithdrawalStatus;
import com.ptit.parking.payment.domain.ExchangeRate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Cài đặt {@link WithdrawalRequestRepository} bằng SQL thuần. Thời điểm lưu theo UTC. */
@Component
class WithdrawalRequestJdbcAdapter implements WithdrawalRequestRepository {

    private static final String SELECT = """
            SELECT id, owner_id, points, cash_per_point, status, created_at, cancelled_at, processed_by, processed_at,
                   reject_reason, transaction_id, confirmed_at, complaint_note, complained_at, resolution_note,
                   resolved_by, resolved_at
            FROM withdrawal_requests
            """;

    private final JdbcClient jdbc;

    WithdrawalRequestJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public WithdrawalRequest save(WithdrawalRequest request) {
        if (request.id() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.sql("""
                    INSERT INTO withdrawal_requests (owner_id, points, cash_per_point, status, created_at)
                    VALUES (:owner, :points, :rate, :status, :createdAt)""")
                    .param("owner", request.ownerId())
                    .param("points", request.points())
                    .param("rate", request.rate().cashPerPoint())
                    .param("status", request.status().name())
                    .param("createdAt", utc(request.createdAt()))
                    .update(keys);
            return request.withId(keys.getKey().longValue());
        }
        jdbc.sql("""
                UPDATE withdrawal_requests
                SET status = :status, cancelled_at = :cancelledAt, processed_by = :processedBy,
                    processed_at = :processedAt, reject_reason = :rejectReason, transaction_id = :transactionId,
                    confirmed_at = :confirmedAt, complaint_note = :complaintNote, complained_at = :complainedAt,
                    resolution_note = :resolutionNote, resolved_by = :resolvedBy, resolved_at = :resolvedAt
                WHERE id = :id""")
                .param("status", request.status().name())
                .param("cancelledAt", utc(request.cancelledAt()))
                .param("processedBy", request.processedBy())
                .param("processedAt", utc(request.processedAt()))
                .param("rejectReason", request.rejectReason())
                .param("transactionId", request.transactionId())
                .param("confirmedAt", utc(request.confirmedAt()))
                .param("complaintNote", request.complaintNote())
                .param("complainedAt", utc(request.complainedAt()))
                .param("resolutionNote", request.resolutionNote())
                .param("resolvedBy", request.resolvedBy())
                .param("resolvedAt", utc(request.resolvedAt()))
                .param("id", request.id())
                .update();
        return request;
    }

    @Override
    public Optional<WithdrawalRequest> findById(long id) {
        return jdbc.sql(SELECT + "WHERE id = :id").param("id", id).query(WithdrawalRequestJdbcAdapter::map).optional();
    }

    @Override
    public Optional<WithdrawalRequest> lockById(long id) {
        return jdbc.sql(SELECT + "WHERE id = :id FOR UPDATE").param("id", id)
                .query(WithdrawalRequestJdbcAdapter::map).optional();
    }

    @Override
    public List<WithdrawalRequest> findByStatus(WithdrawalStatus status, int limit) {
        return jdbc.sql(SELECT + "WHERE (:status IS NULL OR status = :status) ORDER BY id DESC LIMIT :limit")
                .param("status", status == null ? null : status.name())
                .param("limit", limit)
                .query(WithdrawalRequestJdbcAdapter::map).list();
    }

    @Override
    public List<WithdrawalRequest> findByOwner(long ownerId, int limit) {
        return jdbc.sql(SELECT + "WHERE owner_id = :owner ORDER BY id DESC LIMIT :limit")
                .param("owner", ownerId).param("limit", limit)
                .query(WithdrawalRequestJdbcAdapter::map).list();
    }

    @Override
    public long sumPendingPoints(long ownerId) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(points), 0) FROM withdrawal_requests
                WHERE owner_id = :owner AND status = 'CHO_XU_LY'""")
                .param("owner", ownerId).query(Long.class).single();
    }

    private static WithdrawalRequest map(ResultSet rs, int row) throws SQLException {
        return WithdrawalRequest.restore()
                .id(rs.getLong("id"))
                .ownerId(rs.getLong("owner_id"))
                .points(rs.getLong("points"))
                .rate(new ExchangeRate(rs.getInt("cash_per_point")))
                .status(WithdrawalStatus.valueOf(rs.getString("status")))
                .createdAt(instant(rs, "created_at"))
                .cancelledAt(instant(rs, "cancelled_at"))
                .processedBy(rs.getObject("processed_by", Long.class))
                .processedAt(instant(rs, "processed_at"))
                .rejectReason(rs.getString("reject_reason"))
                .transactionId(rs.getObject("transaction_id", Long.class))
                .confirmedAt(instant(rs, "confirmed_at"))
                .complaintNote(rs.getString("complaint_note"))
                .complainedAt(instant(rs, "complained_at"))
                .resolutionNote(rs.getString("resolution_note"))
                .resolvedBy(rs.getObject("resolved_by", Long.class))
                .resolvedAt(instant(rs, "resolved_at"))
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
