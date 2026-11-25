package com.ptit.parking.student.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.port.out.BicycleCodeIssuer;
import com.ptit.parking.student.application.port.out.VehicleRepository;
import com.ptit.parking.student.application.usecase.RegisterVehicleUseCase.Command;
import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;

/** SV02 — đồng hồ CỐ ĐỊNH (Clock.fixed) nên kiểm tra được chính xác thời điểm đăng ký. */
class RegisterVehicleUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-01T01:00:00.123456789Z");
    static final TransactionRunner DIRECTLY = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private final FakeVehicles vehicles = new FakeVehicles();
    private final FakeBicycleCodes bicycleCodes = new FakeBicycleCodes();
    private final RegisterVehicleUseCase register = new RegisterVehicleUseCase(
            vehicles, bicycleCodes, DIRECTLY, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));

    @Test
    void dangKyThanhCong_bienSoDaChuanHoa_chuXeLaNguoiGoi() {
        Vehicle saved = register.register(new Command(7L, "29a-123.45", "XE_MAY"));

        assertThat(saved.id()).isEqualTo(1L);
        assertThat(saved.studentId()).isEqualTo(7L);
        assertThat(saved.plate()).isEqualTo(new PlateNumber("29A12345"));
        assertThat(saved.vehicleTypeCode()).isEqualTo("XE_MAY");
        assertThat(saved.registeredAt()).isEqualTo(Instant.parse("2026-10-01T01:00:00.123456Z")); // cắt tới micro giây
    }

    @Test
    void bienSoDaCo_duViet_khacKieu_vanBaoPlateExists() {
        register.register(new Command(7L, "29A12345", "XE_MAY"));

        assertThatThrownBy(() -> register.register(new Command(8L, "29a 123.45", "XE_MAY")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.PLATE_EXISTS));
        assertThat(vehicles.saved).hasSize(1);
    }

    @Test
    void xeDapKhongBienSo_baiCapMaXD() {
        Vehicle saved = register.register(new Command(7L, "", "XE_DAP"));

        assertThat(saved.plate().value()).isEqualTo("XD00001");
        assertThat(saved.plate().display()).isEqualTo("XD-00001");
    }

    @Test
    void xeMayKhongBienSo_biTuChoi_khongCapMa() {
        assertThatThrownBy(() -> register.register(new Command(7L, null, "XE_MAY")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.PLATE_REQUIRED));
        assertThat(bicycleCodes.issued).isZero();
    }

    @Test
    void bienSoSaiQuyDinh_biTuChoi() {
        assertThatThrownBy(() -> register.register(new Command(7L, "ABC12345", "XE_MAY")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void daCo3XeDangDung_xeThu4BiTuChoi_ngungMotXeThiDangKyDuoc() {
        register.register(new Command(7L, "29A11111", "XE_MAY"));
        register.register(new Command(7L, "29A22222", "XE_MAY"));
        Vehicle third = register.register(new Command(7L, "29A33333", "XE_MAY"));

        assertThatThrownBy(() -> register.register(new Command(7L, "29A44444", "XE_MAY")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VEHICLE_LIMIT_REACHED));

        third.deactivate(NOW);
        assertThat(register.register(new Command(7L, "29A44444", "XE_MAY")).id()).isNotNull();
    }

    @Test
    void bienSoCuaXeDaNgung_nguoiKhacDangKyLaiDuoc() {
        Vehicle old = register.register(new Command(7L, "29A12345", "XE_MAY"));
        old.deactivate(NOW);

        assertThat(register.register(new Command(8L, "29A-123.45", "XE_MAY")).studentId()).isEqualTo(8L);
    }

    /** Kho xe giả trong bộ nhớ: giữ CHÍNH đối tượng đã lưu nên test đổi trạng thái xe trực tiếp được. */
    static final class FakeVehicles implements VehicleRepository {
        final List<Vehicle> saved = new ArrayList<>();

        @Override
        public boolean plateExists(PlateNumber plate) {
            return saved.stream().anyMatch(v -> v.isActive() && v.plate().equals(plate));
        }

        @Override
        public Vehicle save(Vehicle vehicle) {
            Vehicle withId = vehicle.withId(saved.size() + 1);
            saved.add(withId);
            return withId;
        }

        @Override
        public List<Vehicle> findByStudentId(long studentId) {
            return saved.stream().filter(v -> v.studentId() == studentId).toList();
        }

        @Override
        public Optional<Vehicle> findById(long vehicleId) {
            return saved.stream().filter(v -> v.id() == vehicleId).findFirst();
        }

        @Override
        public long countActiveForUpdate(long studentId) {
            return saved.stream().filter(v -> v.studentId() == studentId && v.isActive()).count();
        }

        @Override
        public void update(Vehicle vehicle) {
            // đối tượng trong danh sách chính là đối tượng đã sửa
        }
    }

    static final class FakeBicycleCodes implements BicycleCodeIssuer {
        int issued;

        @Override
        public PlateNumber nextCode() {
            return PlateNumber.bicycleCode(++issued);
        }
    }
}
