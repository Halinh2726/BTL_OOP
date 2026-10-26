package com.ptit.parking.payment.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * TaiKhoanDiem — ví điểm của sinh viên hoặc chủ tài khoản.
 * <p><b>Đóng gói:</b> {@code balance} là private và KHÔNG có setter; chỉ thay đổi qua
 * {@link #credit(long)} / {@link #debit(long)}. Vì vậy bất biến R01 "điểm không bao giờ âm"
 * được chính đối tượng bảo đảm, ở MỌI nơi gọi — không phụ thuộc việc service có "nhớ kiểm tra" hay không.
 */
public final class Wallet {

    private final Long id;          // null khi chưa lưu CSDL
    private final long userId;      // chủ ví (users.id)
    private long balance;

    public Wallet(Long id, long userId, long balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("balance < 0"); // dữ liệu hỏng: không bao giờ được xảy ra (R01)
        }
        this.id = id;
        this.userId = userId;
        this.balance = balance;
    }

    /** SV01: mỗi sinh viên mới đăng ký có một ví 0 điểm. */
    public static Wallet openFor(long userId) {
        return new Wallet(null, userId, 0);
    }

    /** Cộng điểm (đổi tiền QL01, nhận phí HT01). */
    public void credit(long points) {
        requirePositive(points);
        balance = Math.addExact(balance, points);   // tràn số -> ném lỗi thay vì âm thầm sai
    }

    /** Trừ điểm (thanh toán HT01, rút điểm QL02). Không đủ điểm -> lỗi, số dư giữ nguyên. */
    public void debit(long points) {
        requirePositive(points);
        if (!hasAtLeast(points)) {
            throw new DomainException(ErrorCode.INSUFFICIENT_POINTS);
        }
        balance -= points;
    }

    /** BV01 (R04): khi xe vào chỉ KIỂM TRA đủ mức phí thấp nhất, không trừ. */
    public boolean hasAtLeast(long points) {
        return balance >= points;
    }

    public Long id() { return id; }

    public long userId() { return userId; }

    public long balance() { return balance; }

    private static void requirePositive(long points) {
        if (points <= 0) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT);
        }
    }
}
