package com.ptit.parking.parking.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.InMemoryParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.InMemoryVehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase.Preview;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** BV02 — tra cứu trước khi cho xe ra: lượt đang mở, chủ xe (R07), phí tạm tính theo giờ hiện tại (R03). */
class PreviewCheckOutUseCaseTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant ENTERED = Instant.parse("2026-10-05T00:55:00Z");        // 07:55 giờ VN
    private static final Instant AT_18_30 = Instant.parse("2026-10-05T11:30:00Z");       // 18:30 giờ VN -> 5.000
    private static final Instant AT_17_59 = Instant.parse("2026-10-05T10:59:00Z");       // 17:59 giờ VN -> 3.000
    private static final long VEHICLE = 100L;
    private static final long STUDENT = 3L;
    private static final VehicleOwner OWNER = new VehicleOwner(VEHICLE, new PlateNumber("29A12345"), "XE_MAY",
            STUDENT, "B21DCCN001", "Nguyễn Văn A");

    private final InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory();
    private final InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository();
    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final TimeSlotFeePolicy feePolicy = new TimeSlotFeePolicy(List.of(
            new FeeRule(LocalTime.MIDNIGHT, 3000), new FeeRule(LocalTime.of(18, 0), 5000)), VN);

    PreviewCheckOutUseCaseTest() {
        vehicles.add(OWNER);
    }

    private PreviewCheckOutUseCase at(Instant now) {
        return new PreviewCheckOutUseCase(vehicles, sessions, wallets, feePolicy, Clock.fixed(now, VN));
    }

    @Test
    void duDiem_choRa_kemLuotDangMo_chuXe_phiTamTinhTheoGioHienTai() {
        long wallet = wallets.insert(new Wallet(null, STUDENT, 10_000));
        ParkingSession open = sessions.markOpen(VEHICLE, ENTERED);

        Preview preview = at(AT_18_30).preview("29a-123.45");

        assertThat(preview.allowed()).isTrue();
        assertThat(preview.reason()).isNull();
        assertThat(preview.owner()).isEqualTo(OWNER);                     // MSSV + họ tên để đối chiếu thẻ (R07)
        assertThat(preview.session().id()).isEqualTo(open.id());
        assertThat(preview.session().enteredAt()).isEqualTo(ENTERED);
        assertThat(preview.balance()).isEqualTo(10_000);
        assertThat(preview.fee()).isEqualTo(5_000);                       // ra sau 18:00
        assertThat(wallets.balanceOf(wallet)).isEqualTo(10_000);          // chỉ đọc: KHÔNG trừ điểm
        assertThat(sessions.all()).allMatch(ParkingSession::isOpen);      // chỉ đọc: KHÔNG đóng lượt
        assertThat(vehicles.locked()).isEmpty();                          // chỉ đọc: KHÔNG khóa
    }

    @Test
    void phiTinhTheoGioRa_khongPhaiGioVao_R03() {
        wallets.insert(new Wallet(null, STUDENT, 10_000));
        sessions.markOpen(VEHICLE, ENTERED);

        assertThat(at(AT_17_59).preview("29A12345").fee()).isEqualTo(3_000);
        assertThat(at(AT_18_30).preview("29A12345").fee()).isEqualTo(5_000);
    }

    @Test
    void dungBangPhi_choRa_thieu1Diem_khongChoRa_vanHienPhiVaLuot() {
        long wallet = wallets.insert(new Wallet(null, STUDENT, 5_000));
        sessions.markOpen(VEHICLE, ENTERED);
        assertThat(at(AT_18_30).preview("29A12345").allowed()).isTrue();

        Wallet w = wallets.lockById(wallet);
        w.debit(1);
        wallets.save(w);

        Preview preview = at(AT_18_30).preview("29A12345");
        assertThat(preview.allowed()).isFalse();
        assertThat(preview.reason()).isEqualTo(ErrorCode.INSUFFICIENT_POINTS);
        assertThat(preview.fee()).isEqualTo(5_000);
        assertThat(preview.balance()).isEqualTo(4_999);
        assertThat(preview.session()).isNotNull();
    }

    @Test
    void xeKhongCoLuotDangMo_khongChoRa_vanHienChuXeVaSoDiem() {
        wallets.insert(new Wallet(null, STUDENT, 10_000));

        Preview preview = at(AT_18_30).preview("29A12345");

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.reason()).isEqualTo(ErrorCode.NO_OPEN_SESSION);
        assertThat(preview.owner()).isEqualTo(OWNER);
        assertThat(preview.balance()).isEqualTo(10_000);
        assertThat(preview.session()).isNull();
        assertThat(preview.fee()).isNull();
    }

    @Test
    void bienSoChuaDangKy_khongCoThongTinGi() {
        Preview preview = at(AT_18_30).preview("30B99999");

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.reason()).isEqualTo(ErrorCode.PLATE_NOT_FOUND);
        assertThat(preview.owner()).isNull();
        assertThat(preview.session()).isNull();
        assertThat(preview.balance()).isNull();
        assertThat(preview.fee()).isNull();
    }

    @Test
    void bienSoSaiDinhDang_baoValidationError() {
        assertThatThrownBy(() -> at(AT_18_30).preview("29-A1"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}