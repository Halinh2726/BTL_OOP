package com.ptit.parking.parking.application.usecase;

import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * BV02 bước 1–2 (Đặc tả API #14) — bảo vệ nhập biển số xe ra, xem lượt gửi đang mở, chủ xe (đối chiếu thẻ — R07),
 * số điểm và PHÍ TẠM TÍNH theo giờ hiện tại. CHỈ ĐỌC: không khóa, không trừ điểm — lúc xác nhận (API #15)
 * phí được tính lại theo giờ xác nhận.
 * <p>Phí do hệ thống tính (R06): bảo vệ chỉ XEM, không sửa được; ví nhận phí không bao giờ trả ra ở đây.
 */
public class PreviewCheckOutUseCase {

    /**
     * {@code owner} null khi biển số chưa đăng ký; {@code session}, {@code fee} null khi xe không có lượt đang mở;
     * {@code reason} null khi được cho ra.
     */
    public record Preview(VehicleOwner owner, ParkingSession session, Long balance, Long fee,
                          boolean allowed, ErrorCode reason) {}

    private final VehicleOwnerDirectory vehicles;
    private final ParkingSessionRepository sessions;
    private final WalletRepository wallets;
    private final FeePolicy feePolicy;
    private final Clock clock;

    public PreviewCheckOutUseCase(VehicleOwnerDirectory vehicles, ParkingSessionRepository sessions,
                                  WalletRepository wallets, FeePolicy feePolicy, Clock clock) {
        this.vehicles = vehicles;
        this.sessions = sessions;
        this.wallets = wallets;
        this.feePolicy = feePolicy;
        this.clock = clock;
    }

    public Preview preview(String rawPlate) {
        PlateNumber plate = PlateNumber.tryParse(rawPlate)
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_ERROR));

        Optional<VehicleOwner> found = vehicles.findByPlate(plate);
        if (found.isEmpty()) {
            return new Preview(null, null, null, null, false, ErrorCode.PLATE_NOT_FOUND);
        }
        VehicleOwner owner = found.get();
        Wallet wallet = wallets.findByUserId(owner.studentUserId()).orElseThrow();

        Optional<ParkingSession> open = sessions.findOpenByVehicle(owner.vehicleId());
        if (open.isEmpty()) {
            return new Preview(owner, null, wallet.balance(), null, false, ErrorCode.NO_OPEN_SESSION);
        }
        long fee = feePolicy.feeAt(ZonedDateTime.now(clock));               // R03: phí theo giờ RA — ở đây là bây giờ
        boolean enough = wallet.hasAtLeast(fee);
        return new Preview(owner, open.get(), wallet.balance(), fee, enough,
                enough ? null : ErrorCode.INSUFFICIENT_POINTS);
    }
}
