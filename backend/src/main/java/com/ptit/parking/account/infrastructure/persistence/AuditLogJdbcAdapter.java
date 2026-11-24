package com.ptit.parking.account.infrastructure.persistence;

import com.ptit.parking.account.application.port.out.AuditLogQuery;
import com.ptit.parking.shared.application.port.out.AuditTrail;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link AuditTrail} (ghi) và {@link AuditLogQuery} (đọc) trên bảng audit_logs. Chỉ INSERT, không UPDATE/DELETE. */
@Component
class AuditLogJdbcAdapter implements AuditTrail, AuditLogQuery {

    private final JdbcClient jdbc;

    AuditLogJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AuditEntry entry) {
        jdbc.sql("""
                INSERT INTO audit_logs (actor_id, action, target_user_id, detail, created_at)
                VALUES (:actor, :action, :target, :detail, :at)""")
                .param("actor", entry.actorId())
                .param("action", entry.action().name())
                .param("target", entry.targetUserId())
                .param("detail", entry.detail())
                .param("at", LocalDateTime.ofInstant(entry.at(), ZoneOffset.UTC))
                .update();
    }

    @Override
    public List<AuditRecord> page(Long targetUserId, Long before, int limit) {
        return jdbc.sql("""
                SELECT a.id, a.actor_id, actor.full_name AS actor_name, a.action, a.target_user_id,
                       target.full_name AS target_name, a.detail, a.created_at
                FROM audit_logs a
                JOIN users actor ON actor.id = a.actor_id
                LEFT JOIN users target ON target.id = a.target_user_id
                WHERE (:target IS NULL OR a.target_user_id = :target)
                  AND (:before IS NULL OR a.id < :before)
                ORDER BY a.id DESC
                LIMIT :limit""")
                .param("target", targetUserId)
                .param("before", before)
                .param("limit", limit)
                .query((rs, row) -> new AuditRecord(rs.getLong("id"), rs.getLong("actor_id"),
                        rs.getString("actor_name"), AuditAction.valueOf(rs.getString("action")),
                        rs.getObject("target_user_id", Long.class), rs.getString("target_name"),
                        rs.getString("detail"),
                        rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)))
                .list();
    }
}
