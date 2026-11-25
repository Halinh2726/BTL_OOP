package com.ptit.parking.parking.application.usecase;

import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.port.out.VietQrGenerator;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.FeeQuote;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.parking.domain.VietQrOrder;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * BV02 bước 1–2 (Đặc tả API #14) — bảo vệ nhập biển số xe ra, xem lượt gửi đang mở, chủ xe (đối chiếu thẻ — R07),
 * số điểm, PHÍ TẠM TÍNH theo giờ hiện tại (hoặc phí qua đêm theo Mục 3.9), kiểm tra giờ khóa (từ 22:00)
 * và sinh mã VietQR động nếu thiếu điểm. CHỈ ĐỌC: không khóa, không trừ điểm.
 */
public class PreviewCheckOutUseCase {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * {@code owner} null khi biển số chưa đăng ký; {@code session}, {@code fee} null khi xe không có lượt đang mở;
     * {@code reason} null khi được cho ra.
     */
    public record Preview(VehicleOwner owner, ParkingSession session, Long balance, Long fee,
                          boolean allowed, ErrorCode reason, LocalDate passValidUntil,
                          boolean overnight, String curfewTime, VietQrOrder vietQrOrder) {

        /** Tương thích ngược với các test trước Đợt 5. */
        public Preview(VehicleOwner owner, ParkingSession session, Long balance, Long fee, boolean allowed,
                       ErrorCode reason) {
            this(owner, session, balance, fee, allowed, reason, null, false, null, null);
        }

        /** Đợt 5: {@code passValidUntil} khác null khi xe có vé tháng (phí 0). */
        public Preview(VehicleOwner owner, ParkingSession session, Long balance, Long fee, boolean allowed,
                       ErrorCode reason, LocalDate passValidUntil) {
            this(owner, session, balance, fee, allowed, reason, passValidUntil, false, null, null);
        }
    }

    private final VehicleOwnerDirectory vehicles;
    private final ParkingSessionRepository sessions;
    private final WalletRepository wallets;
    private final FeePolicy feePolicy;
    private final VietQrGenerator vietQrGenerator;
    private final Clock clock;

    public PreviewCheckOutUseCase(VehicleOwnerDirectory vehicles, ParkingSessionRepository sessions,
                                  WalletRepository wallets, FeePolicy feePolicy, Clock clock) {
        this(vehicles, sessions, wallets, feePolicy, null, clock);
    }

    public PreviewCheckOutUseCase(VehicleOwnerDirectory vehicles, ParkingSessionRepository sessions,
                                  WalletRepository wallets, FeePolicy feePolicy,
                                  VietQrGenerator vietQrGenerator, Clock clock) {
        this.vehicles = vehicles;
        this.sessions = sessions;
        this.wallets = wallets;
        this.feePolicy = feePolicy;
        this.vietQrGenerator = vietQrGenerator;
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
        ZonedDateTime nowZoned = ZonedDateTime.now(clock);
        FeeQuote quote = feePolicy.quoteSession(owner.vehicleId(), open.get().enteredAt(), nowZoned);

        if (feePolicy.isCheckoutLocked(nowZoned)) {
            String curfew = feePolicy.policyConfig().checkoutCurfewTime().format(HH_MM);
            return new Preview(owner, open.get(), wallet.balance(), quote.points(), false,
                    ErrorCode.CHECKOUT_CURFEW, quote.passValidUntil(), quote.overnight(), curfew, null);
        }

        boolean enough = wallet.hasAtLeast(quote.points());
        VietQrOrder qr = (!enough && vietQrGenerator != null && quote.points() > 0)
                ? vietQrGenerator.generate(open.get().id(), owner.plate().value(), owner.studentCode(),
                        owner.fullName(), quote.points())
                : null;
        return new Preview(owner, open.get(), wallet.balance(), quote.points(), enough,
                enough ? null : ErrorCode.INSUFFICIENT_POINTS, quote.passValidUntil(),
                quote.overnight(), null, qr);
    }
}
