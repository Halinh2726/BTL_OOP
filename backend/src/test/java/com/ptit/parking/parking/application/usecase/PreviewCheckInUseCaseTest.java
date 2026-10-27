package com.ptit.parking.parking.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.InMemoryParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.PreviewCheckInUseCase.Preview;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** BV01 — tra cứu trước khi cho xe vào: đủ thông tin để đối chiếu thẻ, và kết luận kèm lý do. */
class PreviewCheckInUseCaseTest {

    private static final long VEHICLE = 100L;
    private static final long STUDENT = 3L;
    private static final VehicleOwner OWNER = new VehicleOwner(VEHICLE, new PlateNumber("29A12345"), "XE_MAY",
            STUDENT, "B21DCCN001", "Nguyễn Văn A");

    private final VehicleOwnerDirectory vehicles = plate -> plate.equals(OWNER.plate()) ? Optional.of(OWNER) : Optional.empty();
    private final InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository();
    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final TimeSlotFeePolicy feePolicy = new TimeSlotFeePolicy(List.of(
            new FeeRule(LocalTime.MIDNIGHT, 3000), new FeeRule(LocalTime.of(18, 0), 5000)), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final PreviewCheckInUseCase previewCheckIn = new PreviewCheckInUseCase(vehicles, sessions, wallets, feePolicy);

    @Test
    void duDiem_khongCoLuotMo_choVao_kemThongTinDoiChieuThe() {
        wallets.insert(new Wallet(null, STUDENT, 10_000));

        Preview preview = previewCheckIn.preview("29a-123.45");          // bảo vệ gõ kiểu nào cũng được

        assertThat(preview.allowed()).isTrue();
        assertThat(preview.reason()).isNull();
        assertThat(preview.owner()).isEqualTo(OWNER);                     // MSSV + họ tên để đối chiếu thẻ
        assertThat(preview.balance()).isEqualTo(10_000);
        assertThat(preview.minFee()).isEqualTo(3_000);                    // mức thấp nhất của bảng giá, không phải 5.000
    }

    @Test
    void dungBang3000_vanChoVao_2999_thiKhong() {
        long wallet = wallets.insert(new Wallet(null, STUDENT, 3_000));
        assertThat(previewCheckIn.preview("29A12345").allowed()).isTrue();

        Wallet w = wallets.lockById(wallet);
        w.debit(1);
        wallets.save(w);

        Preview preview = previewCheckIn.preview("29A12345");
        assertThat(preview.allowed()).isFalse();
        assertThat(preview.reason()).isEqualTo(ErrorCode.INSUFFICIENT_POINTS);    // R04
        assertThat(preview.balance()).isEqualTo(2_999);
    }

    @Test
    void xeDangGui_khongChoVao_R05() {
        wallets.insert(new Wallet(null, STUDENT, 10_000));
        sessions.markOpen(VEHICLE);

        Preview preview = previewCheckIn.preview("29A12345");

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.reason()).isEqualTo(ErrorCode.SESSION_ALREADY_OPEN);
    }

    @Test
    void bienSoChuaDangKy_khongChoVao_khongCoThongTinChuXe() {
        Preview preview = previewCheckIn.preview("30B99999");

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.reason()).isEqualTo(ErrorCode.PLATE_NOT_FOUND);
        assertThat(preview.owner()).isNull();
        assertThat(preview.balance()).isNull();
        assertThat(preview.minFee()).isEqualTo(3_000);
    }

    @Test
    void bienSoSaiDinhDang_baoValidationError() {
        assertThatThrownBy(() -> previewCheckIn.preview("29-A1"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}