package com.ptit.parking.owner.application.usecase;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.owner.application.port.out.OwnerFinanceQuery;
import com.ptit.parking.owner.application.port.out.OwnerFinanceQuery.DailyRevenue;
import com.ptit.parking.owner.application.port.out.WithdrawalRequestRepository;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Chủ bãi xe xem tài chính của bãi — TỰ xem để kiểm tra chéo, không phải tin con số quản lý báo cáo.
 * Chỉ đọc. Cùng dữ liệu quản lý dùng để đối soát, nhưng khác mục đích.
 * <p>"Theo phương thức thanh toán": điểm không mang nguồn gốc, nên phương thức là kênh TIỀN đi vào hệ thống (đổi điểm
 * bằng tiền mặt) và đi ra (rút điểm nhận tiền mặt). Bản này mới có kênh tiền mặt; kênh điện tử cần nạp điểm online.
 */
public class OwnerFinanceUseCase {

    public static final long MAX_RANGE_DAYS = 93;

    /**
     * @param balance         số điểm hiện có trong ví chủ bãi
     * @param pendingPoints   số điểm đang nằm trong các yêu cầu rút chờ xử lý
     * @param available       số điểm còn có thể tạo yêu cầu rút (R15)
     * @param cashHeld        quỹ tiền mặt quản lý đang giữ (R13)
     */
    public record Overview(String ownerName, long balance, long pendingPoints, long available, long revenueToday,
                           long paymentsToday, long revenueMonth, long cashHeld, int cashPerPoint) {}

    private final OwnerFinanceQuery finance;
    private final WithdrawalRequestRepository requests;
    private final WalletRepository wallets;
    private final ExchangeRateRepository exchangeRates;
    private final Clock clock;

    public OwnerFinanceUseCase(OwnerFinanceQuery finance, WithdrawalRequestRepository requests, WalletRepository wallets,
                               ExchangeRateRepository exchangeRates,
                               Clock clock) {
        this.finance = finance;
        this.requests = requests;
        this.wallets = wallets;
        this.exchangeRates = exchangeRates;
        this.clock = clock;
    }

    public Overview overview() {
        OwnerFinanceQuery.ReceivingAccount account = finance.receivingAccount();
        long balance = wallets.findByUserId(account.ownerUserId()).orElseThrow().balance();
        long pending = requests.sumPendingPoints(account.ownerUserId());
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        List<DailyRevenue> month = finance.dailyRevenue(today.withDayOfMonth(1).atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant(), zone);
        DailyRevenue todayRow = month.stream().filter(row -> row.date().equals(today)).findFirst().orElse(null);
        return new Overview(account.ownerName(), balance, pending, Math.max(0, balance - pending),
                todayRow == null ? 0 : todayRow.revenue(), todayRow == null ? 0 : todayRow.payments(),
                month.stream().mapToLong(DailyRevenue::revenue).sum(), finance.cashHeldByManager(),
                exchangeRates.currentExchangeRate().cashPerPoint());
    }

    /** Doanh thu theo ngày trong [from, to] (giờ Việt Nam, tối đa 93 ngày). */
    public List<DailyRevenue> daily(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        ZoneId zone = clock.getZone();
        return finance.dailyRevenue(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant(),
                zone);
    }
}
