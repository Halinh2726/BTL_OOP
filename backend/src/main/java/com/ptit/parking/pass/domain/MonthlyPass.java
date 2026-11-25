package com.ptit.parking.pass.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Vé tháng của MỘT xe cho MỘT tháng dương lịch (giờ Việt Nam). Trong tháng đó xe ra không bị trừ phí, xe vào không
 * cần điểm tối thiểu. Giá lưu TẠI THỜI ĐIỂM mua — đổi bảng giá sau này không ảnh hưởng vé đã bán (giống R11).
 * <p>Bất biến: không có setter; vé đã mua không hủy / sửa (muốn hoàn thì dùng giao dịch điều chỉnh).
 */
public final class MonthlyPass {

    private final Long id;
    private final long studentId;
    private final long vehicleId;
    private final YearMonth month;
    private final long pricePoints;
    private final long transactionId;
    private final Instant purchasedAt;

    private MonthlyPass(Long id, long studentId, long vehicleId, YearMonth month, long pricePoints,
                        long transactionId, Instant purchasedAt) {
        this.id = id;
        this.studentId = studentId;
        this.vehicleId = vehicleId;
        this.month = Objects.requireNonNull(month, "month");
        if (pricePoints <= 0) {
            throw new IllegalArgumentException("Giá vé phải > 0");
        }
        this.pricePoints = pricePoints;
        this.transactionId = transactionId;
        this.purchasedAt = Objects.requireNonNull(purchasedAt, "purchasedAt");
    }

    /** Chỉ bán vé cho tháng HIỆN TẠI hoặc tháng KẾ TIẾP (tránh mua trước quá xa rồi bảng giá thay đổi). */
    public static void requirePurchasable(YearMonth month, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        if (month == null || !(month.equals(current) || month.equals(current.plusMonths(1)))) {
            throw new DomainException(ErrorCode.PASS_MONTH_INVALID);
        }
    }

    public static MonthlyPass purchase(long studentId, long vehicleId, YearMonth month, long pricePoints,
                                       long transactionId, Instant now) {
        return new MonthlyPass(null, studentId, vehicleId, month, pricePoints, transactionId,
                now.truncatedTo(ChronoUnit.MICROS));
    }

    public static MonthlyPass restore(long id, long studentId, long vehicleId, YearMonth month, long pricePoints,
                                      long transactionId, Instant purchasedAt) {
        return new MonthlyPass(id, studentId, vehicleId, month, pricePoints, transactionId, purchasedAt);
    }

    /** Vé có hiệu lực vào ngày này không. */
    public boolean covers(LocalDate day) {
        return YearMonth.from(day).equals(month);
    }

    /** Ngày cuối cùng vé còn hiệu lực. */
    public LocalDate validUntil() {
        return month.atEndOfMonth();
    }

    public MonthlyPass withId(long newId) {
        return new MonthlyPass(newId, studentId, vehicleId, month, pricePoints, transactionId, purchasedAt);
    }

    public Long id() { return id; }

    public long studentId() { return studentId; }

    public long vehicleId() { return vehicleId; }

    public YearMonth month() { return month; }

    public long pricePoints() { return pricePoints; }

    public long transactionId() { return transactionId; }

    public Instant purchasedAt() { return purchasedAt; }
}
