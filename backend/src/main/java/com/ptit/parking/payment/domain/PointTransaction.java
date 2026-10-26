package com.ptit.parking.payment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * GiaoDich — lớp TRỪU TƯỢNG, {@code sealed}: chỉ đúng 3 lớp con được phép kế thừa.
 * <p><b>Đa hình:</b> mỗi loại giao dịch tự biết điểm của MỘT ví thay đổi thế nào
 * ({@link #changeFor(long)}); nơi gọi không cần if/else theo loại.
 * <p>R10: không có phương thức nào sửa giao dịch sau khi tạo — mọi field đều final.
 */
public abstract sealed class PointTransaction permits CashDeposit, CashWithdrawal, ParkingPayment {

    private final Long id;                 // null khi chưa lưu CSDL
    private final long points;
    private final UUID idempotencyKey;     // chống xử lý trùng khi bấm xác nhận nhiều lần
    private final long performedBy;        // truy vết: quản lý hoặc bảo vệ thực hiện
    private final Instant createdAt;

    protected PointTransaction(Long id, long points, UUID idempotencyKey, long performedBy, Instant createdAt) {
        if (points <= 0) {
            throw new IllegalArgumentException("points phải > 0");
        }
        this.id = id;
        this.points = points;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.performedBy = performedBy;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    // ===== Phương thức trừu tượng: mỗi lớp con BẮT BUỘC tự cài đặt =====

    public abstract TransactionType type();

    /** Điểm thay đổi với ví {@code walletId}: dương nếu ví được cộng, âm nếu bị trừ, 0 nếu không liên quan. */
    public abstract long changeFor(long walletId);

    /** Số dư của ví {@code walletId} ngay sau giao dịch. */
    public abstract long balanceAfterFor(long walletId);

    // ===== Phương thức cụ thể dùng chung, xây trên các phương thức trừu tượng (Template Method) =====

    public boolean involves(long walletId) {
        return changeFor(walletId) != 0;
    }

    /** Số dư trước = số dư sau − điểm thay đổi. Viết MỘT lần, đúng cho cả 3 loại. */
    public long balanceBeforeFor(long walletId) {
        return balanceAfterFor(walletId) - changeFor(walletId);
    }

    // ===== Getter =====

    public Long id() { return id; }

    public long points() { return points; }

    public UUID idempotencyKey() { return idempotencyKey; }

    public long performedBy() { return performedBy; }

    public Instant createdAt() { return createdAt; }

    // ===== Tiện ích cho lớp con =====

    protected static long requirePersisted(Wallet wallet) {
        return Objects.requireNonNull(wallet.id(), "Ví phải được lưu CSDL trước khi giao dịch");
    }

    protected IllegalArgumentException notInvolved(long walletId) {
        return new IllegalArgumentException("Ví " + walletId + " không liên quan giao dịch " + id);
    }
}
