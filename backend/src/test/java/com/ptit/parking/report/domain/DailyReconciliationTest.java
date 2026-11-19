package com.ptit.parking.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Đợt 4a — biên bản đối soát: quy tắc nằm trong đối tượng (lệch phải giải trình, tiền đếm không âm). */
class DailyReconciliationTest {

    private static final Instant NOW = Instant.parse("2026-11-20T11:00:00Z");

    /** Đầu ngày 100.000, vào 50.000, ra 20.000 -> cuối ngày phải có 130.000 (R13). */
    private static final CashPosition POSITION = new CashPosition(LocalDate.of(2026, 11, 20), 100_000, 50_000, 20_000,
            3, 1, 12, 36_000, true);

    @Test
    void tienPhaiCo_bangDauNgayCongVaoTruRa() {
        assertThat(POSITION.expectedCash()).isEqualTo(130_000);
    }

    @Test
    void demKhop_khongCanGiaiTrinh() {
        DailyReconciliation r = DailyReconciliation.close(POSITION, 130_000, "  ", 1L, NOW);

        assertThat(r.outcome()).isEqualTo(ReconciliationOutcome.KHOP);
        assertThat(r.discrepancy()).isZero();
        assertThat(r.note()).isNull();                                   // chuỗi trắng = không giải trình
        assertThat(r.expectedCash()).isEqualTo(130_000);
        assertThat(r.businessDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(r.closedBy()).isEqualTo(1L);
    }

    @Test
    void lechMaKhongGiaiTrinh_biChan() {
        assertThatThrownBy(() -> DailyReconciliation.close(POSITION, 125_000, null, 1L, NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.RECONCILIATION_NOTE_REQUIRED);
    }

    @Test
    void thieuTien_coGiaiTrinh_ketLuanThieu() {
        DailyReconciliation r = DailyReconciliation.close(POSITION, 125_000, " Trả nhầm 5.000 cho SV01 ", 1L, NOW);

        assertThat(r.outcome()).isEqualTo(ReconciliationOutcome.THIEU);
        assertThat(r.discrepancy()).isEqualTo(-5_000);
        assertThat(r.note()).isEqualTo("Trả nhầm 5.000 cho SV01");
    }

    @Test
    void thuaTien_ketLuanThua() {
        assertThat(DailyReconciliation.close(POSITION, 131_000, "Khách gửi thêm", 1L, NOW).outcome())
                .isEqualTo(ReconciliationOutcome.THUA);
    }

    @Test
    void tienDemAm_hoacGiaiTrinhQuaDai_biChan() {
        assertThatThrownBy(() -> DailyReconciliation.close(POSITION, -1, "x", 1L, NOW))
                .extracting(e -> ((DomainException) e).errorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT);
        assertThatThrownBy(() -> DailyReconciliation.close(POSITION, 1, "x".repeat(501), 1L, NOW))
                .extracting(e -> ((DomainException) e).errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void soLieuTrongNgayAm_khongTaoDuocCashPosition() {
        assertThatThrownBy(() -> new CashPosition(LocalDate.of(2026, 11, 20), 0, -1, 0, 0, 0, 0, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
