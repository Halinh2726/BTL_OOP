package com.ptit.parking.gate.application.port.out;

import com.ptit.parking.gate.domain.ShiftAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Port: bảng phân ca (shift_assignments). */
public interface ShiftAssignmentRepository {

    /** Trùng (cùng ngày, cùng ca, cùng bảo vệ) → uq_shift_assignments (SHIFT_ALREADY_ASSIGNED). */
    ShiftAssignment insert(ShiftAssignment assignment);

    Optional<ShiftAssignment> findById(long id);

    void delete(long id);

    /** Các ca từ ngày {@code from} đến {@code to} (gồm cả hai); {@code guardId} null = mọi bảo vệ. */
    List<ShiftAssignment> findBetween(LocalDate from, LocalDate to, Long guardId);
}
