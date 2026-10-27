package com.ptit.parking.manager.application.usecase;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.manager.application.port.out.OwnerAccountDirectory;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.IdempotencyGuard.Execution;
import com.ptit.parking.payment.application.RequestFingerprint;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.CashWithdrawal;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * QL02 (Đặc tả API #19) — chủ tài khoản rút điểm, quản lý trả tiền mặt. Một transaction:
 * khóa ví chủ tài khoản → {@link CashWithdrawal#execute} trừ điểm (không đủ → INSUFFICIENT_POINTS) → lưu ví
 * → lưu giao dịch RUT_DIEM kèm tỷ lệ (R11).
 * <p>R09: ví nguồn do hệ thống tự xác định — request chỉ có số điểm. Bọc trong {@link IdempotencyGuard}:
 * quản lý bấm "Xác nhận" hai lần cũng chỉ trừ MỘT lần (không trả tiền mặt hai lần).
 */
public class WithdrawCashUseCase {

    public record Command(long managerId, UUID idempotencyKey, long points) {}

    public record Result(CashWithdrawal withdrawal, boolean replayed) {}

    private final OwnerAccountDirectory owners;
    private final WalletRepository wallets;
    private final ExchangeRateRepository exchangeRates;
    private final IdempotencyGuard idempotency;
    private final Clock clock;

    public WithdrawCashUseCase(OwnerAccountDirectory owners, WalletRepository wallets,
                               ExchangeRateRepository exchangeRates, IdempotencyGuard idempotency, Clock clock) {
        this.owners = owners;
        this.wallets = wallets;
        this.exchangeRates = exchangeRates;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public Result withdraw(Command command) {
        String requestHash = RequestFingerprint.of(TransactionType.RUT_DIEM, command.managerId(), command.points());

        Execution<CashWithdrawal> execution = idempotency.execute(command.idempotencyKey(), requestHash,
                CashWithdrawal.class, () -> {
                    ExchangeRate rate = exchangeRates.currentExchangeRate();
                    rate.cashFor(command.points());                  // số điểm ≤ 0 -> INVALID_AMOUNT, TRƯỚC khi khóa ví
                    Wallet ownerWallet = wallets.lockById(owners.ownerAccount().walletId());    // R09
                    CashWithdrawal withdrawal = CashWithdrawal.execute(ownerWallet, command.points(), rate,
                            command.managerId(), command.idempotencyKey(),
                            clock.instant().truncatedTo(ChronoUnit.MICROS));
                    wallets.save(ownerWallet);
                    return withdrawal;
                });
        return new Result(execution.transaction(), execution.replayed());
    }
}
