package com.ptit.parking.owner.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Port CHỈ ĐỌC: số liệu tài chính của bãi xe cho chủ bãi xe (và để kiểm tra R16 khi trả tiền rút điểm). */
public interface OwnerFinanceQuery {

    /** Tài khoản nhận phí gửi xe: chủ bãi xe và ví của họ (system_config.receiving_wallet_id). */
    record ReceivingAccount(long ownerUserId, String ownerName, long walletId) {}

    /**
     * Số liệu một ngày (giờ Việt Nam).
     *
     * @param payments    số lượt xe ra đã thu phí (sinh viên trả bằng điểm và khách)
     * @param revenue     tổng doanh thu quy ra điểm: phí và vé tháng vào ví chủ bãi, cộng tiền khách (kể cả phí mất vé).
     *                    Tiền khách KHÔNG vào ví nên không làm tăng số điểm rút được
     * @param adjustments điểm điều chỉnh ròng vào ví chủ bãi (âm: hoàn điểm cho sinh viên)
     * @param cashIn      tiền mặt quản lý nhận khi đổi điểm (kênh TIỀN MẶT vào hệ thống)
     * @param cashOut     tiền mặt quản lý trả khi rút điểm (kênh TIỀN MẶT ra khỏi hệ thống)
     */
    record DailyRevenue(LocalDate date, long payments, long revenue, long adjustments, long cashIn, long cashOut) {}

    ReceivingAccount receivingAccount();

    /** R13: tiền mặt quản lý đang giữ = tiền nhận khi đổi điểm − tiền trả khi rút điểm (view v_reconciliation). */
    long cashHeldByManager();

    /** Các ngày có phát sinh trong [from, to), theo ngày giờ Việt Nam {@code zone}. */
    List<DailyRevenue> dailyRevenue(Instant from, Instant to, ZoneId zone);

    /** Họ tên theo id người dùng — hiện ai đã trả tiền / từ chối / giải quyết khiếu nại. */
    Map<Long, String> namesOf(Collection<Long> userIds);
}
