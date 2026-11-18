package com.ptit.parking.account.application.usecase;

import com.ptit.parking.account.application.port.out.AuditLogQuery;
import com.ptit.parking.account.application.port.out.AuditLogQuery.AuditRecord;
import java.util.List;

/** Quản lý xem nhật ký thao tác trên tài khoản, mới nhất trước, phân trang theo khóa (giống lịch sử giao dịch). */
public class ListAuditLogUseCase {

    public static final int MAX_LIMIT = 50;

    public record Page(List<AuditRecord> items, boolean hasMore, Long nextBefore) {}

    private final AuditLogQuery logs;

    public ListAuditLogUseCase(AuditLogQuery logs) {
        this.logs = logs;
    }

    public Page page(Long targetUserId, Long before, int limit) {
        List<AuditRecord> rows = logs.page(targetUserId, before, limit + 1);    // lấy thừa 1 dòng để biết còn trang sau
        boolean hasMore = rows.size() > limit;
        List<AuditRecord> items = hasMore ? rows.subList(0, limit) : rows;
        return new Page(items, hasMore, hasMore ? items.get(items.size() - 1).id() : null);
    }
}
