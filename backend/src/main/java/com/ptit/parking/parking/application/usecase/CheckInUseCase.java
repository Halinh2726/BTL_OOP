package com.ptit.parking.parking.application.usecase;

import java.time.Clock;

import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;

/**
 * BV01 (Đặc tả API #13) — bảo vệ xác nhận cho xe vào. Một transaction:
 * khóa dòng xe → chưa có lượt đang mở (R05) → ví đủ mức phí thấp nhất (R04, CHỈ kiểm tra, KHÔNG trừ)
 * → tạo lượt DANG_GUI, bảo vệ cho vào là người đang đăng nhập.
 * <p>Kiểm tra LẠI từ đầu, không tin kết quả preview: giữa lúc tra cứu và lúc bấm xác nhận dữ liệu có thể đã đổi.
 */
public class CheckInUseCase {

    public record Command(long guardId, String plateNumber) {}

    public record Result(ParkingSession session, VehicleOwner vehicle) {}

    private final VehicleOwnerDirectory vehicles;
    private final ParkingSessionRepository sessions;
    private final WalletRepository wallets;
    private final FeePolicy feePolicy;
    private final TransactionRunner transactions;
    private final Clock clock;

    public CheckInUseCase(VehicleOwnerDirectory vehicles, ParkingSessionRepository sessions, WalletRepository wallets,
                          FeePolicy feePolicy, TransactionRunner transactions, Clock clock) {
        this.vehicles = vehicles;
        this.sessions = sessions;
        this.wallets = wallets;
        this.feePolicy = feePolicy;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Result checkIn(Command command) {
        PlateNumber plate = PlateNumber.tryParse(command.plateNumber())
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_ERROR));

        return transactions.inTransaction(() -> {
            // Khóa dòng xe: hai bảo vệ bấm xác nhận cùng một xe thì người sau chờ, rồi thấy lượt người trước vừa tạo
            VehicleOwner vehicle = vehicles.lockByPlate(plate)
                    .orElseThrow(() -> new DomainException(ErrorCode.PLATE_NOT_FOUND));
            if (sessions.hasOpenSession(vehicle.vehicleId())) {
                throw new DomainException(ErrorCode.SESSION_ALREADY_OPEN);                 // R05
            }
            Wallet wallet = wallets.findByUserId(vehicle.studentUserId()).orElseThrow();
            if (!wallet.hasAtLeast(feePolicy.minimumFee())) {
                throw new DomainException(ErrorCode.INSUFFICIENT_POINTS);                  // R04: chỉ kiểm tra, không trừ
            }
            ParkingSession session = ParkingSession.open(vehicle.vehicleId(), command.guardId(), clock.instant());
            return new Result(sessions.save(session), vehicle);
        });
    }
}
