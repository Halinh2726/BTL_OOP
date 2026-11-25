package com.ptit.parking.guest.infrastructure;

import com.ptit.parking.guest.application.GuestParkingStore;
import com.ptit.parking.guest.domain.GuestPayment;
import com.ptit.parking.guest.domain.GuestSession;
import com.ptit.parking.student.domain.PlateNumber;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class GuestParkingJdbcAdapter implements GuestParkingStore {
    private static final String SESSION = "SELECT * FROM guest_sessions";
    private static final String PAYMENT = "SELECT * FROM guest_payments";
    private final JdbcClient jdbc;

    GuestParkingJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Settings settings() {
        return jdbc.sql("SELECT guest_parking_enabled, lost_ticket_fee_vnd FROM system_config WHERE id = 1")
                .query((rs, row) -> new Settings(rs.getBoolean(1), rs.getInt(2))).single();
    }

    @Override
    public void saveSettings(Settings settings) {
        jdbc.sql("""
                UPDATE system_config SET guest_parking_enabled = :enabled, lost_ticket_fee_vnd = :fee WHERE id = 1
                """).param("enabled", settings.enabled()).param("fee", settings.lostTicketFeeVnd()).update();
    }

    @Override
    public int nextTicketNumber(LocalDate date) {
        jdbc.sql("""
                INSERT INTO guest_ticket_sequences (ticket_date, next_number) VALUES (:date, 1)
                ON DUPLICATE KEY UPDATE next_number = next_number + 1
                """).param("date", date).update();
        return jdbc.sql("SELECT next_number FROM guest_ticket_sequences WHERE ticket_date = :date")
                .param("date", date).query(Integer.class).single();
    }

    @Override
    public GuestSession enter(Entry entry, String ticketCode, String token, long guardId, Instant now) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO guest_sessions (ticket_code, ticket_token, plate_number, vehicle_type_code,
                    guest_name, guest_phone, note, entered_at, entry_guard_id)
                VALUES (:code, :token, :plate, :type, :name, :phone, :note, :now, :guard)
                """).param("code", ticketCode).param("token", token)
                .param("plate", entry.plate() == null ? null : entry.plate().value())
                .param("type", entry.vehicleTypeCode()).param("name", entry.name()).param("phone", entry.phone())
                .param("note", entry.note()).param("now", utc(now)).param("guard", guardId)
                .update(keys, "id");
        return new GuestSession(keys.getKey().longValue(), ticketCode, token, entry.plate(), entry.vehicleTypeCode(),
                now, null, false, false);
    }

    @Override
    public Optional<GuestSession> findOpenByPlate(PlateNumber plate) {
        return jdbc.sql(SESSION + " WHERE open_plate = :plate").param("plate", plate.value())
                .query(GuestParkingJdbcAdapter::mapSession).optional();
    }

    @Override
    public Optional<GuestSession> findByCode(String code) {
        return jdbc.sql(SESSION + " WHERE ticket_code = :code").param("code", code)
                .query(GuestParkingJdbcAdapter::mapSession).optional();
    }

    @Override
    public Optional<GuestSession> findByToken(String token) {
        return jdbc.sql(SESSION + " WHERE ticket_token = :token").param("token", token)
                .query(GuestParkingJdbcAdapter::mapSession).optional();
    }

    @Override
    public Optional<GuestSession> lockById(long id) {
        return jdbc.sql(SESSION + " WHERE id = :id FOR UPDATE").param("id", id)
                .query(GuestParkingJdbcAdapter::mapSession).optional();
    }

    @Override
    public void approveLostTicket(long id, long managerId) {
        jdbc.sql("UPDATE guest_sessions SET lost_ticket_approved_by = :manager WHERE id = :id")
                .param("id", id).param("manager", managerId).update();
    }

    @Override
    public void close(long id, long guardId, Instant now, String documentLast4) {
        jdbc.sql("""
                UPDATE guest_sessions SET status = 'DA_RA', exited_at = :now, exit_guard_id = :guard,
                    lost_ticket = :lost, document_last4 = :document WHERE id = :id
                """).param("now", utc(now)).param("guard", guardId)
                .param("lost", documentLast4 != null).param("document", documentLast4).param("id", id).update();
    }

    @Override
    public Optional<GuestPayment> paymentByKey(UUID key) {
        return jdbc.sql(PAYMENT + " WHERE idempotency_key = :key").param("key", key.toString())
                .query(GuestParkingJdbcAdapter::mapPayment).optional();
    }

    @Override
    public Optional<GuestPayment> paymentForSession(long sessionId) {
        return jdbc.sql(PAYMENT + " WHERE guest_session_id = :id").param("id", sessionId)
                .query(GuestParkingJdbcAdapter::mapPayment).optional();
    }

    @Override
    public Optional<VietQrQuote> vietQrQuote(long sessionId) {
        return jdbc.sql("""
                SELECT quote_id, fee_points, amount, overnight, expires_at
                FROM guest_vietqr_quotes WHERE guest_session_id = :id
                """).param("id", sessionId).query((rs, row) -> new VietQrQuote(
                        UUID.fromString(rs.getString("quote_id")), rs.getLong("fee_points"), rs.getLong("amount"),
                        rs.getBoolean("overnight"), instant(rs, "expires_at"))).optional();
    }

    @Override
    public void saveVietQrQuote(long sessionId, VietQrQuote quote) {
        jdbc.sql("""
                INSERT INTO guest_vietqr_quotes (guest_session_id, quote_id, fee_points, amount, overnight, expires_at)
                VALUES (:id, :code, :points, :amount, :overnight, :expiry)
                ON DUPLICATE KEY UPDATE quote_id = :code, fee_points = :points, amount = :amount,
                    overnight = :overnight, expires_at = :expiry
                """).param("id", sessionId).param("code", quote.id().toString())
                .param("points", quote.feePoints()).param("amount", quote.amount())
                .param("overnight", quote.overnight()).param("expiry", utc(quote.expiresAt())).update();
    }

    @Override
    public void insertPayment(GuestPayment payment) {
        jdbc.sql("""
                INSERT INTO guest_payments (guest_session_id, guard_id, shift_id, method, fee_points, amount,
                    overnight, lost_ticket_fee, idempotency_key, paid_at)
                VALUES (:session, :guard, :shift, :method, :points, :amount, :overnight, :lost, :key, :paid)
                """).param("session", payment.sessionId()).param("guard", payment.guardId())
                .param("shift", payment.shiftId()).param("method", payment.method().name())
                .param("points", payment.feePoints()).param("amount", payment.amount())
                .param("overnight", payment.overnight()).param("lost", payment.lostTicketFee())
                .param("key", payment.idempotencyKey().toString()).param("paid", utc(payment.paidAt()))
                .update();
    }

    @Override
    public Page search(Search search) {
        String filters = " WHERE 1 = 1";
        if (search.from() != null) {
            filters += " AND s.entered_at >= :from AND s.entered_at < :until";
        }
        if (!search.status().isEmpty()) {
            filters += " AND s.status = :status";
        }
        if (!search.plate().isEmpty()) {
            filters += " AND s.plate_number LIKE :plate";
        }
        String order = search.overdueBefore() == null ? " ORDER BY s.entered_at DESC, s.id DESC" : " ORDER BY s.entered_at, s.id";
        List<ListedSession> items = bindSearch(jdbc.sql("""
                SELECT s.*, p.method, p.amount FROM guest_sessions s
                LEFT JOIN guest_payments p ON p.guest_session_id = s.id
                """ + filters + order + " LIMIT :limit OFFSET :offset"), search)
                .param("limit", search.limit()).param("offset", search.offset())
                .query(GuestParkingJdbcAdapter::mapListed).list();
        long total = bindSearch(jdbc.sql("SELECT COUNT(*) FROM guest_sessions s" + filters), search)
                .query(Long.class).single();
        long overdue = search.overdueBefore() == null ? 0
                : bindSearch(jdbc.sql("SELECT COUNT(*) FROM guest_sessions s" + filters + " AND s.entered_at < :cutoff"), search)
                        .param("cutoff", utc(search.overdueBefore())).query(Long.class).single();
        return new Page(items, total, overdue);
    }

    private JdbcClient.StatementSpec bindSearch(JdbcClient.StatementSpec statement, Search search) {
        if (search.from() != null) {
            statement.param("from", utc(search.from())).param("until", utc(search.until()));
        }
        if (!search.status().isEmpty()) {
            statement.param("status", search.status());
        }
        if (!search.plate().isEmpty()) {
            statement.param("plate", "%" + search.plate() + "%");
        }
        return statement;
    }

    @Override
    public Revenue revenue(Instant from, Instant until) {
        return jdbc.sql("""
                SELECT COUNT(*) AS payments,
                    COALESCE(SUM(CASE WHEN method = 'TIEN_MAT' THEN amount END), 0) AS cash,
                    COALESCE(SUM(CASE WHEN method = 'VIETQR' THEN amount END), 0) AS vietqr
                FROM guest_payments WHERE paid_at >= :from AND paid_at < :until
                """).param("from", utc(from)).param("until", utc(until))
                .query((rs, row) -> new Revenue(rs.getLong("payments"), rs.getLong("cash"), rs.getLong("vietqr")))
                .single();
    }

    @Override
    public void anonymizeBefore(Instant cutoff) {
        jdbc.sql("""
                UPDATE guest_sessions SET guest_name = NULL, guest_phone = NULL, note = NULL, document_last4 = NULL
                WHERE entered_at < :cutoff AND (guest_name IS NOT NULL OR guest_phone IS NOT NULL OR note IS NOT NULL
                    OR document_last4 IS NOT NULL)
                """).param("cutoff", utc(cutoff)).update();
    }

    private static GuestSession mapSession(ResultSet rs, int row) throws SQLException {
        String plate = rs.getString("plate_number");
        return new GuestSession(rs.getLong("id"), rs.getString("ticket_code"), rs.getString("ticket_token"),
                plate == null ? null : new PlateNumber(plate), rs.getString("vehicle_type_code"),
                instant(rs, "entered_at"), instant(rs, "exited_at"), rs.getBoolean("lost_ticket"),
                rs.getObject("lost_ticket_approved_by") != null);
    }

    private static ListedSession mapListed(ResultSet rs, int row) throws SQLException {
        GuestSession session = mapSession(rs, row);
        String method = rs.getString("method");
        return new ListedSession(session.id(), session.ticketCode(),
                session.plate() == null ? null : session.plate().value(),
                session.plate() == null ? null : session.plate().display(), session.vehicleTypeCode(),
                rs.getString("guest_name"), rs.getString("guest_phone"), rs.getString("note"),
                session.enteredAt(), session.exitedAt(), session.lostTicket(), session.lostTicketApproved(),
                method == null ? null : GuestPayment.Method.valueOf(method), rs.getObject("amount", Long.class));
    }

    private static GuestPayment mapPayment(ResultSet rs, int row) throws SQLException {
        return new GuestPayment(rs.getLong("guest_session_id"), rs.getLong("guard_id"),
                rs.getObject("shift_id", Long.class), GuestPayment.Method.valueOf(rs.getString("method")),
                rs.getLong("fee_points"), rs.getLong("amount"), rs.getBoolean("overnight"),
                rs.getLong("lost_ticket_fee"), UUID.fromString(rs.getString("idempotency_key")),
                instant(rs, "paid_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
