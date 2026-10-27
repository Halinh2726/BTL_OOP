package com.ptit.parking.parking.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * LuotGui — một lượt gửi xe, từ lúc bảo vệ cho xe vào tới lúc cho xe ra.
 * <p><b>Đóng gói:</b> không có setter. Lượt gửi chỉ sinh ra qua {@link #open} (BV01) và chỉ kết thúc qua
 * {@link #close} (BV02); trạng thái, giờ ra, bảo vệ cho ra LUÔN đổi cùng nhau — không thể có lượt "đã ra"
 * mà thiếu giờ ra (đúng ràng buộc ck_parking_sessions_state của CSDL).
 */
public final class ParkingSession {

    private final Long id;                  // null khi chưa lưu CSDL
    private final long vehicleId;
    private final Instant enteredAt;
    private final long entryGuardId;
    private SessionStatus status;
    private Instant exitedAt;
    private Long exitGuardId;

    private ParkingSession(Long id, long vehicleId, Instant enteredAt, long entryGuardId,
                           SessionStatus status, Instant exitedAt, Long exitGuardId) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.enteredAt = Objects.requireNonNull(enteredAt, "enteredAt");
        this.entryGuardId = entryGuardId;
        this.status = status;
        this.exitedAt = exitedAt;
        this.exitGuardId = exitGuardId;
    }

    /**
     * BV01: bảo vệ cho xe vào — lượt mới ở trạng thái DANG_GUI. Thời điểm cắt tới micro giây cho khớp
     * DATETIME(6) của CSDL: giờ vào trả cho bảo vệ và giờ vào dùng tính phí lúc ra là MỘT.
     */
    public static ParkingSession open(long vehicleId, long entryGuardId, Instant now) {
        return new ParkingSession(null, vehicleId, now.truncatedTo(ChronoUnit.MICROS), entryGuardId,
                SessionStatus.DANG_GUI, null, null);
    }

    
    /** Dựng lại lượt gửi đã lưu trong CSDL (chỉ adapter dùng). */
    public static ParkingSession restore(long id, long vehicleId, SessionStatus status, Instant enteredAt,
                                         long entryGuardId, Instant exitedAt, Long exitGuardId) {
        return new ParkingSession(id, vehicleId, enteredAt, entryGuardId, status, exitedAt, exitGuardId);
    }

    /** BV02: bảo vệ cho xe ra. Lượt đã kết thúc thì không đóng lần hai. */
    public void close(long exitGuardId, Instant now) {
        if (!isOpen()) {
            throw new DomainException(ErrorCode.NO_OPEN_SESSION);
        }
        Instant exit = now.truncatedTo(ChronoUnit.MICROS);
        if (exit.isBefore(enteredAt)) {
            throw new IllegalArgumentException("Giờ ra trước giờ vào");   // đồng hồ máy chủ sai: lỗi lập trình/vận hành
        }
        this.status = SessionStatus.DA_RA;
        this.exitedAt = exit;
        this.exitGuardId = exitGuardId;
    }

    /** Bản sao đã có id do CSDL sinh. */
    public ParkingSession withId(long newId) {
        return new ParkingSession(newId, vehicleId, enteredAt, entryGuardId, status, exitedAt, exitGuardId);
    }

    public boolean isOpen() { return status == SessionStatus.DANG_GUI; }

    public Long id() { return id; }

    public long vehicleId() { return vehicleId; }

    public Instant enteredAt() { return enteredAt; }

    public long entryGuardId() { return entryGuardId; }

    public SessionStatus status() { return status; }

    public Instant exitedAt() { return exitedAt; }

    public Long exitGuardId() { return exitGuardId; }
}
