package com.ptit.parking.student.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.port.out.VehicleRepository;
import com.ptit.parking.student.application.usecase.RegisterVehicleUseCase.Command;
import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;

/** SV02 — đồng hồ CỐ ĐỊNH (Clock.fixed) nên kiểm tra được chính xác thời điểm đăng ký. */
class RegisterVehicleUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-01T01:00:00.123456789Z");

    private final FakeVehicles vehicles = new FakeVehicles();
    private final RegisterVehicleUseCase register = new RegisterVehicleUseCase(
            vehicles, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));

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

        assertThatThrownBy(() -> register.register(new Command(8L, "29a 123.45", "XE_DAP")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.PLATE_EXISTS));
        assertThat(vehicles.saved).hasSize(1);
    }

    static final class FakeVehicles implements VehicleRepository {
        final List<Vehicle> saved = new ArrayList<>();

        @Override
        public boolean plateExists(PlateNumber plate) {
            return saved.stream().anyMatch(v -> v.plate().equals(plate));
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
    }
}