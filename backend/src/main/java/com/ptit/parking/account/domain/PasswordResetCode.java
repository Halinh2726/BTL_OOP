package com.ptit.parking.account.domain;

import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Mã OTP 6 số khôi phục mật khẩu. Quy tắc nằm TRONG đối tượng (đóng gói), không rải ở use case:
 * <ul>
 *   <li>Hiệu lực {@link #TTL} = 10 phút, dùng được MỘT lần.</li>
 *   <li>Nhập sai tối đa {@link #MAX_FAILED_ATTEMPTS} = 5 lần; sau đó mã bị vô hiệu dù còn hạn —
 *       mã 6 số có 1 triệu khả năng, 5 lần thử chỉ đoán trúng với xác suất 0,0005%.</li>
 *   <li>Chỉ lưu hash (BCrypt), không lưu mã rõ: lộ CSDL cũng không lộ mã đang hiệu lực.</li>
 * </ul>
 */
public final class PasswordResetCode {

    public static final Duration TTL = Duration.ofMinutes(10);
    public static final int MAX_FAILED_ATTEMPTS = 5;
    /** Không gửi mã mới nếu mã trước mới được gửi chưa tới 1 phút (chống spam email). */
    public static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);

    private final Long id;
    private final long userId;
    private final String codeHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private int failedAttempts;
    private Instant usedAt;

    private PasswordResetCode(Long id, long userId, String codeHash, Instant createdAt, Instant expiresAt,
                              int failedAttempts, Instant usedAt) {
        this.id = id;
        this.userId = userId;
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.failedAttempts = failedAttempts;
        this.usedAt = usedAt;
    }

    public static PasswordResetCode issue(long userId, String rawCode, PasswordHasher hasher, Instant now) {
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        return new PasswordResetCode(null, userId, hasher.hash(rawCode), at, at.plus(TTL), 0, null);
    }

    public static PasswordResetCode restore(long id, long userId, String codeHash, Instant createdAt, Instant expiresAt,
                                            int failedAttempts, Instant usedAt) {
        return new PasswordResetCode(id, userId, codeHash, createdAt, expiresAt, failedAttempts, usedAt);
    }

    /**
     * Thử một mã người dùng nhập. Đúng → đánh dấu đã dùng, trả {@code true}. Sai → tăng số lần sai, trả {@code false}
     * (người gọi PHẢI lưu lại đối tượng để lần sai được tính). Hết hạn / đã dùng / sai quá 5 lần → ném lỗi.
     */
    public boolean verify(String rawCode, PasswordHasher hasher, Instant now) {
        if (usedAt != null || !now.isBefore(expiresAt)) {
            throw new DomainException(ErrorCode.RESET_CODE_EXPIRED);
        }
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            throw new DomainException(ErrorCode.RESET_CODE_LOCKED);
        }
        if (rawCode != null && hasher.matches(rawCode, codeHash)) {
            usedAt = now.truncatedTo(ChronoUnit.MICROS);
            return true;
        }
        failedAttempts++;
        return false;
    }

    /** Mã này mới được gửi gần đây — chưa gửi mã mới (xem {@link #RESEND_INTERVAL}). */
    public boolean issuedRecently(Instant now) {
        return now.isBefore(createdAt.plus(RESEND_INTERVAL));
    }

    public Long id() { return id; }

    public long userId() { return userId; }

    public String codeHash() { return codeHash; }

    public Instant createdAt() { return createdAt; }

    public Instant expiresAt() { return expiresAt; }

    public int failedAttempts() { return failedAttempts; }

    public Instant usedAt() { return usedAt; }
}
