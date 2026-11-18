package com.ptit.parking.payment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ptit.parking.auth.application.port.out.UserRepository;
import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Manager;
import com.ptit.parking.manager.application.usecase.DepositCashUseCase;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.PayParkingFeeByVietQrUseCase;
import com.ptit.parking.payment.application.usecase.ProcessBankWebhookUseCase.BankTransactionItem;
import com.ptit.parking.payment.application.usecase.ProcessBankWebhookUseCase.ProcessResult;
import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho {@link ProcessBankWebhookUseCase} (Webhook SePay / Casso tự động nạp điểm hoặc thanh toán vé).
 */
class ProcessBankWebhookUseCaseTest {

    private final DepositCashUseCase depositCash = mock(DepositCashUseCase.class);
    private final PayParkingFeeByVietQrUseCase payParkingFeeByVietQr = mock(PayParkingFeeByVietQrUseCase.class);
    private final UserRepository users = mock(UserRepository.class);

    private ProcessBankWebhookUseCase useCase;

    @BeforeEach
    void setUp() {
        when(users.findByUsername("quanly01")).thenReturn(Optional.of(
                new Manager(1L, "quanly01", "hash", "Phạm Văn Quản", AccountStatus.HOAT_DONG)));
        useCase = new ProcessBankWebhookUseCase(depositCash, payParkingFeeByVietQr, users);
    }

    @Test
    void napTienVaoVi_cuPhapHopLe_napThanhCong() {
        CashDeposit deposit = CashDeposit.restore(10L, 50000L, UUID.randomUUID(), 1L,
                Instant.now(), 2L, 50000L, 50000L, new ExchangeRate(1));
        when(depositCash.deposit(any())).thenReturn(new DepositCashUseCase.Result(deposit, "B21DCCN001", false));

        BankTransactionItem item = new BankTransactionItem(
                "MBBank", 50000L, "NAP B21DCCN001 CHUYEN TIEN", "FT24084998242", "in");

        List<ProcessResult> results = useCase.process(List.of(item));

        assertThat(results).hasSize(1);
        ProcessResult res = results.get(0);
        assertThat(res.action()).isEqualTo("NAP");
        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.studentCode()).isEqualTo("B21DCCN001");
        assertThat(res.amount()).isEqualTo(50000L);
        assertThat(res.replayed()).isFalse();

        verify(depositCash).deposit(any());
        verify(payParkingFeeByVietQr, never()).payByVietQr(any());
    }

    @Test
    void napTienVaoVi_khongCoKhoangTrang_vanNhanDienDuoc() {
        CashDeposit deposit = CashDeposit.restore(10L, 20000L, UUID.randomUUID(), 1L,
                Instant.now(), 2L, 20000L, 20000L, new ExchangeRate(1));
        when(depositCash.deposit(any())).thenReturn(new DepositCashUseCase.Result(deposit, "B21DCCN001", false));

        BankTransactionItem item = new BankTransactionItem(
                "MBBank", 20000L, "NAPB21DCCN001", "FT24084998243", "in");

        List<ProcessResult> results = useCase.process(List.of(item));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).studentCode()).isEqualTo("B21DCCN001");
        assertThat(results.get(0).status()).isEqualTo("SUCCESS");
    }

    @Test
    void napTienVaoVi_sinhVienKhongTonTai_traVeFailed() {
        when(depositCash.deposit(any())).thenThrow(new DomainException(ErrorCode.STUDENT_NOT_FOUND));

        BankTransactionItem item = new BankTransactionItem(
                "MBBank", 50000L, "NAP B99DCCN999", "FT24084998244", "in");

        List<ProcessResult> results = useCase.process(List.of(item));

        assertThat(results).hasSize(1);
        ProcessResult res = results.get(0);
        assertThat(res.action()).isEqualTo("NAP");
        assertThat(res.status()).isEqualTo("FAILED");
        assertThat(res.message()).contains("STUDENT_NOT_FOUND");
    }

    @Test
    void thanhToanGuiXe_cuPhapTTGX_thanhToanThanhCong() {
        ParkingPayment payment = ParkingPayment.restore(5L, 3000L, UUID.randomUUID(), 1L,
                Instant.now(), 42L, 2L, 1L, 0L, 3000L);
        VehicleOwner owner = new VehicleOwner(10L, new PlateNumber("29A12345"), "XE_MAY",
                3L, "B21DCCN001", "Nguyễn Văn A");
        when(payParkingFeeByVietQr.payByVietQr(any())).thenReturn(
                new PayParkingFeeByVietQrUseCase.Result(payment, owner, false));

        BankTransactionItem item = new BankTransactionItem(
                "Vietcombank", 3000L, "TTGX 42 THANH TOAN GUI XE", "VCB987654321", "in");

        List<ProcessResult> results = useCase.process(List.of(item));

        assertThat(results).hasSize(1);
        ProcessResult res = results.get(0);
        assertThat(res.action()).isEqualTo("TTGX");
        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.sessionId()).isEqualTo(42L);
        assertThat(res.amount()).isEqualTo(3000L);

        verify(payParkingFeeByVietQr).payByVietQr(any());
        verify(depositCash, never()).deposit(any());
    }

    @Test
    void giaoDichChieuRa_boQua() {
        BankTransactionItem item = new BankTransactionItem(
                "MBBank", 50000L, "NAP B21DCCN001", "FT24084998245", "out");

        List<ProcessResult> results = useCase.process(List.of(item));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("SKIPPED");
        verify(depositCash, never()).deposit(any());
    }

    @Test
    void noiDungKhongKhopCuPhap_traVeIgnored() {
        BankTransactionItem item = new BankTransactionItem(
                "MBBank", 50000L, "Chuyen tien an trua", "FT24084998246", "in");

        List<ProcessResult> results = useCase.process(List.of(item));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("IGNORED");
        verify(depositCash, never()).deposit(any());
        verify(payParkingFeeByVietQr, never()).payByVietQr(any());
    }
}

