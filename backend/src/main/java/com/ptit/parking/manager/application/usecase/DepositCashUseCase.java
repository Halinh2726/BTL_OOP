package com.ptit.parking.manager.application.usecase;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.manager.application.port.out.StudentDirectory;
import com.ptit.parking.manager.application.port.out.StudentDirectory.StudentAccount;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.IdempotencyGuard.Execution;
import com.ptit.parking.payment.application.RequestFingerprint;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * QL01 (Đặc tả API #17) — quản lý nhận tiền mặt, cộng điểm vào ví sinh viên. Một transaction:
 * khóa ví sinh viên → {@link CashDeposit#execute} cộng điểm → lưu ví → lưu giao dịch DOI_DIEM kèm tỷ lệ (R11).
 * <p>Bọc trong {@link IdempotencyGuard}: quản lý bấm "Xác nhận" hai lần cũng chỉ cộng điểm MỘT lần.
 */
public class DepositCashUseCase {

    public record Command(long managerId, UUID idempotencyKey, String studentCode, long cashAmount) {}

    public record Result(CashDeposit deposit, String studentCode, boolean replayed) {}

    private final StudentDirectory students;
    private final WalletRepository wallets;
    private final ExchangeRateRepository exchangeRates;
    private final IdempotencyGuard idempotency;
    private final Clock clock;

    public DepositCashUseCase(StudentDirectory students, WalletRepository wallets, ExchangeRateRepository exchangeRates,
                              IdempotencyGuard idempotency, Clock clock) {
        this.students = students;
        this.wallets = wallets;
        this.exchangeRates = exchangeRates;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public Result deposit(Command command) {
        String studentCode = StudentDirectory.normalize(command.studentCode());
        String requestHash = RequestFingerprint.of(TransactionType.DOI_DIEM, command.managerId(),
                studentCode, command.cashAmount());

        Execution<CashDeposit> execution = idempotency.execute(command.idempotencyKey(), requestHash,
                CashDeposit.class, () -> {
                    ExchangeRate rate = exchangeRates.currentExchangeRate();
                    rate.pointsFor(command.cashAmount());          // R02: kiểm tra số tiền TRƯỚC khi tra sinh viên
                    StudentAccount student = students.findByStudentCode(studentCode)
                            .orElseThrow(() -> new DomainException(ErrorCode.STUDENT_NOT_FOUND));
                    Wallet wallet = wallets.lockById(student.walletId());
                    CashDeposit deposit = CashDeposit.execute(wallet, command.cashAmount(), rate, command.managerId(),
                            command.idempotencyKey(), clock.instant().truncatedTo(ChronoUnit.MICROS));
                    wallets.save(wallet);
                    return deposit;
                });
        return new Result(execution.transaction(), studentCode, execution.replayed());
    }
}