package com.ptit.parking.parking.application.usecase;

import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.util.Optional;

/**
 * BV01 bước 1–2 (Đặc tả API #12) — bảo vệ nhập biển số, xem chủ xe (MSSV, họ tên để đối chiếu thẻ), số điểm
 * và mức phí thấp nhất, cùng kết luận "có được cho vào không". CHỈ ĐỌC: không khóa, không ghi — lúc xác nhận
 * (API #13) hệ thống kiểm tra lại toàn bộ.
 * <p>Không cho vào vẫn trả kết quả bình thường kèm lý do, để bảo vệ thấy đủ thông tin và giải thích cho sinh viên.
 */
public class PreviewCheckInUseCase {

    /** {@code owner}, {@code balance} là null khi biển số chưa đăng ký; {@code reason} null khi được cho vào. */
    public record Preview(PlateNumber plate, VehicleOwner owner, Long balance, long minFee,
                          boolean allowed, ErrorCode reason) {}

    private final VehicleOwnerDirectory vehicles;
    private final ParkingSessionRepository sessions;
    private final WalletRepository wallets;
    private final FeePolicy feePolicy;

    public PreviewCheckInUseCase(VehicleOwnerDirectory vehicles, ParkingSessionRepository sessions,
                                 WalletRepository wallets, FeePolicy feePolicy) {
        this.vehicles = vehicles;
        this.sessions = sessions;
        this.wallets = wallets;
        this.feePolicy = feePolicy;
    }

    public Preview preview(String rawPlate) {
        PlateNumber plate = PlateNumber.tryParse(rawPlate)
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_ERROR));
        long minFee = feePolicy.minimumFee();                               // R04: mức kiểm tra khi xe vào

        Optional<VehicleOwner> found = vehicles.findByPlate(plate);
        if (found.isEmpty()) {
            return new Preview(plate, null, null, minFee, false, ErrorCode.PLATE_NOT_FOUND);
        }
        VehicleOwner owner = found.get();
        Wallet wallet = wallets.findByUserId(owner.studentUserId()).orElseThrow();

        // Cùng thứ tự kiểm tra với lúc xác nhận (API #13): lượt đang mở trước, rồi tới số điểm
        ErrorCode reason = null;
        if (sessions.hasOpenSession(owner.vehicleId())) {
            reason = ErrorCode.SESSION_ALREADY_OPEN;                         // R05
        } else if (!wallet.hasAtLeast(minFee)) {
            reason = ErrorCode.INSUFFICIENT_POINTS;                          // R04: chỉ kiểm tra, không trừ
        }
        return new Preview(plate, owner, wallet.balance(), minFee, reason == null, reason);
    }
}