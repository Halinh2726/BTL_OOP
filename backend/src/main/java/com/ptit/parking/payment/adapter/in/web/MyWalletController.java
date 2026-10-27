package com.ptit.parking.payment.adapter.in.web;

import com.ptit.parking.payment.application.usecase.GetMyTransactionsUseCase;
import com.ptit.parking.payment.application.usecase.GetMyWalletUseCase;
import com.ptit.parking.payment.domain.PointTransaction;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.shared.security.SessionUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Ví của tôi" — quyền WALLET_VIEW_OWN (sinh viên, chủ tài khoản). Ví luôn là ví của người đang đăng nhập:
 * không có tham số nào để xem ví người khác.
 */
@RestController
@RequestMapping("/api/v1/me")
class MyWalletController {

    /** Response của API #7. */
    record WalletResponse(long balance) {}

    /** Một dòng lịch sử (API #8) — nhìn TỪ GÓC của ví người đang xem. */
    record TransactionItem(long id, String type, long change, long balanceBefore, long balanceAfter, String createdAt) {

        /** Đa hình: không có if/else theo loại giao dịch — mỗi lớp con tự tính điểm thay đổi với ví này. */
        static TransactionItem of(PointTransaction transaction, long walletId, ZoneId zone) {
            return new TransactionItem(transaction.id(), transaction.type().name(),
                    transaction.changeFor(walletId),
                    transaction.balanceBeforeFor(walletId),
                    transaction.balanceAfterFor(walletId),
                    OffsetDateTime.ofInstant(transaction.createdAt(), zone).truncatedTo(ChronoUnit.SECONDS)
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    /** Response của API #8. */
    record TransactionPage(List<TransactionItem> items, boolean hasMore, Long nextBefore) {}

    private final GetMyWalletUseCase getMyWallet;
    private final GetMyTransactionsUseCase getMyTransactions;
    private final ZoneId zone;

    MyWalletController(GetMyWalletUseCase getMyWallet, GetMyTransactionsUseCase getMyTransactions, Clock clock) {
        this.getMyWallet = getMyWallet;
        this.getMyTransactions = getMyTransactions;
        this.zone = clock.getZone();
    }

    /** API #7 — số điểm hiện có. */
    @GetMapping("/wallet")
    WalletResponse wallet(@AuthenticationPrincipal SessionUser user) {
        return new WalletResponse(getMyWallet.walletOf(user.id()).balance());
    }

    /** API #8 — lịch sử giao dịch, mới nhất trước; lọc theo loại; phân trang bằng {@code before}. */
    @GetMapping("/transactions")
    TransactionPage transactions(@AuthenticationPrincipal SessionUser user,
                                 @RequestParam(required = false) TransactionType type,
                                 @RequestParam(defaultValue = "20")
                                 @Min(value = 1, message = "limit từ 1 đến 50")
                                 @Max(value = GetMyTransactionsUseCase.MAX_LIMIT, message = "limit từ 1 đến 50") int limit,
                                 @RequestParam(required = false) @Positive(message = "before phải > 0") Long before) {
        GetMyTransactionsUseCase.Page page = getMyTransactions.history(user.id(), type, before, limit);
        List<TransactionItem> items = page.items().stream()
                .map(transaction -> TransactionItem.of(transaction, page.walletId(), zone))
                .toList();
        return new TransactionPage(items, page.hasMore(), page.nextBefore());
    }
}