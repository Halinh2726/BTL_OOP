package com.ptit.parking.owner.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Đợt 3 — vòng đời yêu cầu rút điểm: 6 trạng thái, đúng người, R18 (người tạo không tự xử lý). */
class WithdrawalRequestTest {

    private static final long OWNER = 2L;
    private static final long MANAGER = 1L;
    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

    private static WithdrawalRequest pending() {
        return WithdrawalRequest.create(OWNER, 5_000, new ExchangeRate(1), NOW).withId(10L);
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(expected));
    }

    @Test
    void taoYeuCau_choXuLy_soTienTheoTyLeLucTao() {
        WithdrawalRequest request = WithdrawalRequest.create(OWNER, 5_000, new ExchangeRate(2), NOW);

        assertThat(request.status()).isEqualTo(WithdrawalStatus.CHO_XU_LY);
        assertThat(request.cashAmount()).isEqualTo(10_000);
        assertCode(() -> WithdrawalRequest.create(OWNER, 0, new ExchangeRate(1), NOW), ErrorCode.INVALID_AMOUNT);
    }

    @Test
    void luongDayDu_traTien_khieuNai_quanLyKiemTraXong() {
        WithdrawalRequest request = pending();

        request.markPaid(MANAGER, 900L, NOW);
        assertThat(request.status()).isEqualTo(WithdrawalStatus.DA_TRA);
        assertThat(request.transactionId()).isEqualTo(900L);

        request.complain(OWNER, "Mới nhận 4.000 đ", NOW);
        assertThat(request.status()).isEqualTo(WithdrawalStatus.KHIEU_NAI);

        request.resolveComplaint(MANAGER, "Đã đếm lại camera quầy, trả bù 1.000 đ", NOW);
        assertThat(request.status()).isEqualTo(WithdrawalStatus.DA_XAC_NHAN);
    }

    @Test
    void chuBaiXacNhanDaNhan_hoanTat_khongAiSuaNua() {
        WithdrawalRequest request = pending();
        request.markPaid(MANAGER, 900L, NOW);
        request.confirmReceived(OWNER, NOW);

        assertThat(request.status()).isEqualTo(WithdrawalStatus.DA_XAC_NHAN);
        assertCode(() -> request.complain(OWNER, "x", NOW), ErrorCode.WITHDRAWAL_STATUS_CONFLICT);
        assertCode(() -> request.reject(MANAGER, "x", NOW), ErrorCode.WITHDRAWAL_STATUS_CONFLICT);
    }

    @Test
    void huyKhiConCho_traTienRoiThiKhongHuyDuoc() {
        WithdrawalRequest cancelled = pending();
        cancelled.cancel(OWNER, NOW);
        assertThat(cancelled.status()).isEqualTo(WithdrawalStatus.DA_HUY);
        assertCode(() -> cancelled.markPaid(MANAGER, 1L, NOW), ErrorCode.WITHDRAWAL_STATUS_CONFLICT);

        WithdrawalRequest paid = pending();
        paid.markPaid(MANAGER, 900L, NOW);
        assertCode(() -> paid.cancel(OWNER, NOW), ErrorCode.WITHDRAWAL_STATUS_CONFLICT);
    }

    @Test
    void R18_nguoiTaoKhongTuXuLy_nguoiKhacKhongThaoTacThayChuBai() {
        WithdrawalRequest request = pending();

        assertCode(() -> request.markPaid(OWNER, 900L, NOW), ErrorCode.SELF_PROCESSING_FORBIDDEN);
        assertCode(() -> request.reject(OWNER, "tự từ chối", NOW), ErrorCode.SELF_PROCESSING_FORBIDDEN);
        assertCode(() -> request.cancel(MANAGER, NOW), ErrorCode.WITHDRAWAL_NOT_FOUND);
    }

    @Test
    void tuChoi_batBuocCoLyDo() {
        assertCode(() -> pending().reject(MANAGER, "  ", NOW), ErrorCode.VALIDATION_ERROR);
    }

    @ParameterizedTest
    @EnumSource(value = WithdrawalStatus.class, names = {"DA_XAC_NHAN", "TU_CHOI", "DA_HUY"})
    void trangThaiKetThuc_khongDiDauDuocNua(WithdrawalStatus terminal) {
        for (WithdrawalStatus next : WithdrawalStatus.values()) {
            assertThat(terminal.canMoveTo(next)).isFalse();
        }
    }
}
