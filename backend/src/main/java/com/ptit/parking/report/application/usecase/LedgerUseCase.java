package com.ptit.parking.report.application.usecase;

import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.report.application.port.out.LedgerQuery;
import com.ptit.parking.report.application.port.out.LedgerQuery.Entry;
import com.ptit.parking.report.application.port.out.LedgerQuery.Filter;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Đợt 4a — sổ giao dịch toàn hệ thống cho quản lý: lọc theo khoảng ngày (giờ Việt Nam), loại giao dịch, MSSV;
 * phân trang theo khóa giống lịch sử của sinh viên; xuất CSV báo cáo (tối đa 93 ngày mỗi lần).
 */
public class LedgerUseCase {

    public static final int DEFAULT_LIMIT = 30;
    public static final int MAX_LIMIT = 100;
    public static final long MAX_EXPORT_DAYS = 93;
    public static final int MAX_EXPORT_ROWS = 20_000;

    /** Điều kiện lọc do người dùng chọn; null = không lọc. */
    public record Criteria(LocalDate from, LocalDate to, TransactionType type, String studentCode) {}

    public record Page(List<Entry> items, boolean hasMore, Long nextBefore) {}

    private final LedgerQuery ledger;
    private final Clock clock;

    public LedgerUseCase(LedgerQuery ledger, Clock clock) {
        this.ledger = ledger;
        this.clock = clock;
    }

    public Page page(Criteria criteria, Long before, int limit) {
        if (criteria.from() != null && criteria.to() != null && criteria.from().isAfter(criteria.to())) {
            throw new DomainException(ErrorCode.DATE_RANGE_INVALID);
        }
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        // Lấy THỪA một dòng: có dòng thứ size+1 nghĩa là còn trang sau
        List<Entry> rows = ledger.find(filter(criteria, before, size + 1));
        boolean hasMore = rows.size() > size;
        List<Entry> items = hasMore ? rows.subList(0, size) : rows;
        return new Page(items, hasMore, hasMore ? items.get(items.size() - 1).id() : null);
    }

    /** Toàn bộ giao dịch trong khoảng ngày để xuất CSV — bắt buộc có khoảng ngày, tối đa 93 ngày. */
    public List<Entry> export(Criteria criteria) {
        if (criteria.from() == null || criteria.to() == null || criteria.from().isAfter(criteria.to())
                || ChronoUnit.DAYS.between(criteria.from(), criteria.to()) >= MAX_EXPORT_DAYS) {
            throw new DomainException(ErrorCode.DATE_RANGE_INVALID);
        }
        return ledger.find(filter(criteria, null, MAX_EXPORT_ROWS));
    }

    private Filter filter(Criteria criteria, Long before, int limit) {
        Instant from = criteria.from() == null ? null : criteria.from().atStartOfDay(clock.getZone()).toInstant();
        Instant to = criteria.to() == null ? null : criteria.to().plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        String code = criteria.studentCode() == null || criteria.studentCode().isBlank()
                ? null : criteria.studentCode().strip().toUpperCase(Locale.ROOT);
        return new Filter(from, to, criteria.type(), code, before, limit);
    }
}
