package com.ptit.parking.owner.application.usecase;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.owner.application.port.out.OwnerFinanceQuery;
import com.ptit.parking.owner.application.port.out.WithdrawalRequestRepository;
import com.ptit.parking.owner.domain.WithdrawalRequest;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phía CHỦ BÃI XE của yêu cầu rút điểm: tạo, hủy (khi còn chờ), xác nhận đã nhận đủ tiền, báo chưa nhận.
 * <p>R15 khi tạo: số điểm yêu cầu + tổng các yêu cầu đang chờ ≤ số điểm hiện có. Kiểm tra sau khi KHÓA ví chủ bãi,
 * nên hai yêu cầu tạo cùng lúc không thể cùng "giữ chỗ" một số điểm.
 */
public class OwnerWithdrawalUseCase {

    public static final int LIST_LIMIT = 50;

    private final WithdrawalRequestRepository requests;
    private final OwnerFinanceQuery finance;
    private final WalletRepository wallets;
    private final ExchangeRateRepository exchangeRates;
    private final TransactionRunner transactions;
    private final Clock clock;

    public OwnerWithdrawalUseCase(WithdrawalRequestRepository requests, OwnerFinanceQuery finance,
                                  WalletRepository wallets, ExchangeRateRepository exchangeRates,
                                  TransactionRunner transactions, Clock clock) {
        this.requests = requests;
        this.finance = finance;
        this.wallets = wallets;
        this.exchangeRates = exchangeRates;
        this.transactions = transactions;
        this.clock = clock;
    }

    public WithdrawalRequest create(long ownerId, long points) {
        OwnerFinanceQuery.ReceivingAccount account = requireReceivingOwner(ownerId);
        WithdrawalRequest request = WithdrawalRequest.create(ownerId, points, exchangeRates.currentExchangeRate(),
                clock.instant());
        return transactions.inTransaction(() -> {
            Wallet wallet = wallets.lockById(account.walletId());
            long reserved = requests.sumPendingPoints(ownerId);
            if (reserved + points > wallet.balance()) {                    // R15
                throw new DomainException(ErrorCode.WITHDRAWAL_EXCEEDS_AVAILABLE);
            }
            return requests.save(request);
        });
    }

    public WithdrawalRequest cancel(long ownerId, long requestId) {
        return change(requestId, request -> request.cancel(ownerId, clock.instant()));
    }

    public WithdrawalRequest confirmReceived(long ownerId, long requestId) {
        return change(requestId, request -> request.confirmReceived(ownerId, clock.instant()));
    }

    public WithdrawalRequest complain(long ownerId, long requestId, String note) {
        return change(requestId, request -> request.complain(ownerId, note, clock.instant()));
    }

    public List<WithdrawalRequest> mine(long ownerId) {
        return requests.findByOwner(ownerId, LIST_LIMIT);
    }

    private WithdrawalRequest change(long requestId, Consumer<WithdrawalRequest> action) {
        return transactions.inTransaction(() -> {
            WithdrawalRequest request = requests.lockById(requestId)
                    .orElseThrow(() -> new DomainException(ErrorCode.WITHDRAWAL_NOT_FOUND));
            action.accept(request);                        // domain tự kiểm tra đúng người, đúng trạng thái
            return requests.save(request);
        });
    }

    /** Chỉ chủ của ví nhận phí mới rút được điểm của ví đó. */
    private OwnerFinanceQuery.ReceivingAccount requireReceivingOwner(long ownerId) {
        OwnerFinanceQuery.ReceivingAccount account = finance.receivingAccount();
        if (account.ownerUserId() != ownerId) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        return account;
    }
}
