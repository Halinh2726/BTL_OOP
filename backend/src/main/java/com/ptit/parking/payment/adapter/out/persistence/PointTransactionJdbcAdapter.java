package com.ptit.parking.payment.adapter.out.persistence;

import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.domain.PointTransaction;
import com.ptit.parking.payment.domain.TransactionType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/**
 * Cài đặt {@link PointTransactionRepository}: một giao dịch = một dòng point_transactions (phần chung)
 * + một dòng ở bảng chi tiết của đúng loại — cùng kiểu ánh xạ kế thừa như users/students.
 * Phải gọi BÊN TRONG transaction của use case để dòng cha và dòng chi tiết cùng được ghi hoặc cùng hủy.
 */
@Component
class PointTransactionJdbcAdapter implements PointTransactionRepository {

    private static final String SELECT = """
            SELECT t.id, t.type, t.points, t.source_wallet_id, t.target_wallet_id,
                   t.source_balance_after, t.target_balance_after, t.idempotency_key, t.request_hash,
                   t.performed_by, t.created_at, c.cash_amount, c.cash_per_point, p.session_id
            FROM point_transactions t
            LEFT JOIN cash_details c ON c.transaction_id = t.id
            LEFT JOIN parking_payment_details p ON p.transaction_id = t.id
            """;

    private final JdbcClient jdbc;

    PointTransactionJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long save(PointTransaction transaction, String requestHash) {
        PointTransactionRow row = PointTransactionRow.from(transaction);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO point_transactions (type, points, source_wallet_id, target_wallet_id,
                    source_balance_after, target_balance_after, idempotency_key, request_hash, performed_by, created_at)
                VALUES (:type, :points, :sourceWalletId, :targetWalletId,
                    :sourceBalanceAfter, :targetBalanceAfter, :idempotencyKey, :requestHash, :performedBy, :createdAt)""")
                .param("type", row.type().name())
                .param("points", row.points())
                .param("sourceWalletId", row.sourceWalletId())
                .param("targetWalletId", row.targetWalletId())
                .param("sourceBalanceAfter", row.sourceBalanceAfter())
                .param("targetBalanceAfter", row.targetBalanceAfter())
                .param("idempotencyKey", row.idempotencyKey().toString())
                .param("requestHash", requestHash)
                .param("performedBy", row.performedBy())
                .param("createdAt", LocalDateTime.ofInstant(row.createdAt(), ZoneOffset.UTC))
                .update(keys);
        long id = keys.getKey().longValue();

        if (row.cashAmount() != null) {
            jdbc.sql("INSERT INTO cash_details (transaction_id, cash_amount, cash_per_point) VALUES (:id, :cash, :rate)")
                    .param("id", id)
                    .param("cash", row.cashAmount())
                    .param("rate", row.cashPerPoint())
                    .update();
        }
        if (row.sessionId() != null) {
            jdbc.sql("INSERT INTO parking_payment_details (transaction_id, session_id) VALUES (:id, :sessionId)")
                    .param("id", id)
                    .param("sessionId", row.sessionId())
                    .update();
        }
        return id;
    }

    @Override
    public Optional<Recorded> findByIdempotencyKey(UUID idempotencyKey) {
        return jdbc.sql(SELECT + "WHERE t.idempotency_key = :key")
                .param("key", idempotencyKey.toString())
                .query((rs, n) -> new Recorded(toRow(rs).toDomain(), rs.getString("request_hash")))
                .optional();
    }

    @Override
    public List<PointTransaction> findPageForWallet(long walletId, TransactionType type, Long before, int limit) {
        return jdbc.sql(SELECT + """
                WHERE (t.source_wallet_id = :walletId OR t.target_wallet_id = :walletId)
                  AND (:type IS NULL OR t.type = :type)
                  AND (:before IS NULL OR t.id < :before)
                ORDER BY t.id DESC
                LIMIT :limit""")
                .param("walletId", walletId)
                .param("type", type == null ? null : type.name())
                .param("before", before)
                .param("limit", limit)
                .query((rs, n) -> toRow(rs).toDomain())
                .list();
    }

    private static PointTransactionRow toRow(ResultSet rs) throws SQLException {
        return new PointTransactionRow(
                rs.getLong("id"),
                TransactionType.valueOf(rs.getString("type")),
                rs.getLong("points"),
                rs.getObject("source_wallet_id", Long.class),
                rs.getObject("target_wallet_id", Long.class),
                rs.getObject("source_balance_after", Long.class),
                rs.getObject("target_balance_after", Long.class),
                UUID.fromString(rs.getString("idempotency_key")),
                rs.getLong("performed_by"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getObject("cash_amount", Long.class),
                rs.getObject("cash_per_point", Integer.class),
                rs.getObject("session_id", Long.class));
    }
}