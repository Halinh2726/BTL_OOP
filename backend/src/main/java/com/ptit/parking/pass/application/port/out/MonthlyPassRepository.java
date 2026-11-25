package com.ptit.parking.pass.application.port.out;

import com.ptit.parking.pass.domain.MonthlyPass;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** Port: vé tháng đã bán (monthly_passes) — chỉ THÊM và ĐỌC. */
public interface MonthlyPassRepository {

    /** Trùng (cùng xe, cùng tháng) → uq_monthly_passes_vehicle_month (PASS_ALREADY_EXISTS). */
    MonthlyPass insert(MonthlyPass pass);

    Optional<MonthlyPass> findFor(long vehicleId, YearMonth month);

    /** Các vé của sinh viên, tháng mới nhất trước. */
    List<MonthlyPass> findByStudent(long studentId);

    default List<MonthlyPass> findByTransactionId(long transactionId) {
        return List.of();
    }

    default List<MonthlyPass> findForVehicleInMonths(long vehicleId, List<YearMonth> months) {
        return months.stream().map(m -> findFor(vehicleId, m)).flatMap(Optional::stream).toList();
    }
}
