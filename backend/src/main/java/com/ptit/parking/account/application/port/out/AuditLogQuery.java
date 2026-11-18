package com.ptit.parking.account.application.port.out;

import com.ptit.parking.shared.application.port.out.AuditTrail.AuditAction;
import java.time.Instant;
import java.util.List;

/** Port: đọc nhật ký thao tác cho quản lý. Ghi thì qua {@link com.ptit.parking.shared.application.port.out.AuditTrail}. */
public interface AuditLogQuery {

    record AuditRecord(long id, long actorId, String actorName, AuditAction action,
                       Long targetUserId, String targetName, String detail, Instant at) {}

    /**
     * Một trang nhật ký, id giảm dần.
     *
     * @param targetUserId chỉ lấy thao tác trên tài khoản này; {@code null} = mọi tài khoản
     * @param before       chỉ lấy id nhỏ hơn; {@code null} = trang đầu
     */
    List<AuditRecord> page(Long targetUserId, Long before, int limit);
}
