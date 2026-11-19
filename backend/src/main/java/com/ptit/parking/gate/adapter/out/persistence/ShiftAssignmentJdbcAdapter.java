package com.ptit.parking.gate.adapter.out.persistence;

import com.ptit.parking.gate.application.port.out.ShiftAssignmentRepository;
import com.ptit.parking.gate.domain.ShiftAssignment;
import com.ptit.parking.gate.domain.ShiftSlot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Cài đặt {@link ShiftAssignmentRepository} trên bảng shift_assignments. */
@Component
class ShiftAssignmentJdbcAdapter implements ShiftAssignmentRepository {

    private static final String SELECT =
            "SELECT id, work_date, slot, guard_id, assigned_by, assigned_at FROM shift_assignments ";

    private final JdbcClient jdbc;

    ShiftAssignmentJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ShiftAssignment insert(ShiftAssignment a) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO shift_assignments (work_date, slot, guard_id, assigned_by, assigned_at) "
                        + "VALUES (:date, :slot, :guard, :by, :at)")
                .param("date", a.workDate())
                .param("slot", a.slot().name())
                .param("guard", a.guardId())
                .param("by", a.assignedBy())
                .param("at", LocalDateTime.ofInstant(a.assignedAt(), ZoneOffset.UTC))
                .update(keys);
        return a.withId(keys.getKeyAs(Number.class).longValue());
    }

    @Override
    public Optional<ShiftAssignment> findById(long id) {
        return jdbc.sql(SELECT + "WHERE id = :id").param("id", id).query(ShiftAssignmentJdbcAdapter::map).optional();
    }

    @Override
    public void delete(long id) {
        jdbc.sql("DELETE FROM shift_assignments WHERE id = :id").param("id", id).update();
    }

    @Override
    public List<ShiftAssignment> findBetween(LocalDate from, LocalDate to, Long guardId) {
        String guardFilter = guardId == null ? "" : "AND guard_id = :guard ";
        var query = jdbc.sql(SELECT + "WHERE work_date BETWEEN :from AND :to " + guardFilter + "ORDER BY work_date, slot")
                .param("from", from)
                .param("to", to);
        if (guardId != null) {
            query = query.param("guard", guardId);
        }
        return query.query(ShiftAssignmentJdbcAdapter::map).list();
    }

    private static ShiftAssignment map(ResultSet rs, int row) throws SQLException {
        return ShiftAssignment.restore(rs.getLong("id"), rs.getObject("work_date", LocalDate.class),
                ShiftSlot.valueOf(rs.getString("slot")), rs.getLong("guard_id"), rs.getLong("assigned_by"),
                rs.getObject("assigned_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }
}
