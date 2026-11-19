package com.ptit.parking.report.application.port.out;

import com.ptit.parking.report.domain.DailyReconciliation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Port: biên bản đối soát — chỉ THÊM và ĐỌC (CSDL chặn sửa / xóa). */
public interface ReconciliationRepository {

    /** Biên bản kèm họ tên người chốt (để hiển thị). */
    record Closed(DailyReconciliation reconciliation, String closedByName) {}

    /** Lưu biên bản mới; trả về bản có id. Trùng ngày -> uq_daily_reconciliations_date (ALREADY_RECONCILED). */
    DailyReconciliation save(DailyReconciliation reconciliation);

    Optional<Closed> findByDate(LocalDate date);

    /** Các biên bản gần nhất, ngày mới nhất trước. */
    List<Closed> recent(int limit);
}
