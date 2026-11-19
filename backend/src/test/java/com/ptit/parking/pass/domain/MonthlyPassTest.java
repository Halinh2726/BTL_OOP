package com.ptit.parking.pass.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Đợt 5 — vé tháng: chỉ bán tháng này / tháng sau, hiệu lực trọn tháng dương lịch. */
class MonthlyPassTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 12, 20);

    @Test
    void chiMuaThangNayHoacThangSau_quaNamVanDung() {
        MonthlyPass.requirePurchasable(YearMonth.of(2026, 12), TODAY);
        MonthlyPass.requirePurchasable(YearMonth.of(2027, 1), TODAY);
        for (YearMonth wrong : new YearMonth[] {YearMonth.of(2026, 11), YearMonth.of(2027, 2), null}) {
            assertThatThrownBy(() -> MonthlyPass.requirePurchasable(wrong, TODAY))
                    .extracting(e -> ((DomainException) e).errorCode()).isEqualTo(ErrorCode.PASS_MONTH_INVALID);
        }
    }

    @Test
    void hieuLucTronThang_namNhuan() {
        MonthlyPass february = MonthlyPass.purchase(3L, 7L, YearMonth.of(2028, 2), 100_000, 55L,
                Instant.parse("2028-01-30T03:00:00Z"));
        assertThat(february.validUntil()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(february.covers(LocalDate.of(2028, 2, 1))).isTrue();
        assertThat(february.covers(LocalDate.of(2028, 2, 29))).isTrue();
        assertThat(february.covers(LocalDate.of(2028, 3, 1))).isFalse();
        assertThat(february.covers(LocalDate.of(2028, 1, 31))).isFalse();
    }

    @Test
    void giaPhaiDuong() {
        assertThatThrownBy(() -> MonthlyPass.purchase(3L, 7L, YearMonth.of(2026, 12), 0, 1L, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
