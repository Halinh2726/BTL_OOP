package com.ptit.parking.student.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Objects;

/**
 * Xe của sinh viên (SV02). Mỗi xe thuộc đúng một sinh viên; biển số là {@link PlateNumber} nên luôn
 * đã chuẩn hóa và hợp lệ. Không đổi chủ, không đổi biển (đổi biển = đăng ký xe mới).
 * <p>Đợt 1 — vòng đời xe. Chỉ đổi được qua hai hành vi, cả hai đều từ chối khi xe đã ngừng sử dụng:
 * <ul>
 *   <li>{@link #changeType} — sửa loại xe (chọn nhầm xe máy / xe đạp điện...).</li>
 *   <li>{@link #deactivate} — "xóa xe": chuyển NGUNG_SU_DUNG, KHÔNG xóa dòng vì lượt gửi cũ còn tham chiếu.</li>
 * </ul>
 * "Xe đang trong bãi thì không sửa/ngừng" cần dữ liệu lượt gửi nên do use case kiểm tra trước khi gọi.
 */
public final class Vehicle {

    /** Mỗi sinh viên tối đa 3 xe ĐANG DÙNG; xe đã ngừng không tính. */
    public static final int MAX_ACTIVE_PER_STUDENT = 3;

    /** Thứ tự hiển thị "xe của tôi" (Đặc tả API #5): xe đăng ký gần nhất lên đầu; cùng thời điểm thì id lớn trước. */
    public static final Comparator<Vehicle> NEWEST_FIRST =
            Comparator.comparing(Vehicle::registeredAt).thenComparing(Vehicle::id).reversed();

    /** Xe đang dùng lên trước, xe đã ngừng xuống cuối; trong mỗi nhóm theo {@link #NEWEST_FIRST}. */
    public static final Comparator<Vehicle> ACTIVE_FIRST =
            Comparator.comparing(Vehicle::isActive).reversed().thenComparing(NEWEST_FIRST);

    private final Long id;                  // null khi chưa lưu CSDL
    private final long studentId;
    private final PlateNumber plate;
    private String vehicleTypeCode;
    private final Instant registeredAt;
    private VehicleStatus status;
    private Instant deactivatedAt;

    public Vehicle(Long id, long studentId, PlateNumber plate, String vehicleTypeCode, Instant registeredAt) {
        this(id, studentId, plate, vehicleTypeCode, registeredAt, VehicleStatus.DANG_DUNG, null);
    }

    public Vehicle(Long id, long studentId, PlateNumber plate, String vehicleTypeCode, Instant registeredAt,
                   VehicleStatus status, Instant deactivatedAt) {
        this.id = id;
        this.studentId = studentId;
        this.plate = Objects.requireNonNull(plate, "plate");
        this.vehicleTypeCode = Objects.requireNonNull(vehicleTypeCode, "vehicleTypeCode");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.status = Objects.requireNonNull(status, "status");
        this.deactivatedAt = deactivatedAt;
        // Trạng thái và thời điểm ngừng luôn đi cùng nhau (khớp ck_vehicles_state)
        if ((status == VehicleStatus.NGUNG_SU_DUNG) != (deactivatedAt != null)) {
            throw new IllegalArgumentException("Trạng thái xe và thời điểm ngừng sử dụng không khớp");
        }
    }

    /**
     * SV02: sinh viên đăng ký xe mới. Thời điểm cắt tới micro giây cho khớp độ chính xác DATETIME(6)
     * của CSDL — giá trị trả về cho người dùng và giá trị đọc lại sau này là MỘT.
     */
    public static Vehicle register(long studentId, PlateNumber plate, String vehicleTypeCode, Instant now) {
        return new Vehicle(null, studentId, plate, vehicleTypeCode, now.truncatedTo(ChronoUnit.MICROS));
    }

    /** Đã có đủ số xe đang dùng thì không đăng ký thêm được. */
    public static void ensureQuota(long activeVehicles) {
        if (activeVehicles >= MAX_ACTIVE_PER_STUDENT) {
            throw new DomainException(ErrorCode.VEHICLE_LIMIT_REACHED);
        }
    }

    /** Bản sao đã có id do CSDL sinh. */
    public Vehicle withId(long newId) {
        return new Vehicle(newId, studentId, plate, vehicleTypeCode, registeredAt, status, deactivatedAt);
    }

    /** @return {@code false} nếu loại xe không đổi (không cần ghi gì). */
    public boolean changeType(String newTypeCode) {
        requireActive();
        String code = Objects.requireNonNull(newTypeCode, "newTypeCode");
        if (code.equals(vehicleTypeCode)) {
            return false;
        }
        vehicleTypeCode = code;
        return true;
    }

    public void deactivate(Instant now) {
        requireActive();
        status = VehicleStatus.NGUNG_SU_DUNG;
        deactivatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isActive() {
        return status == VehicleStatus.DANG_DUNG;
    }

    public boolean belongsTo(long userId) {
        return studentId == userId;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new DomainException(ErrorCode.VEHICLE_INACTIVE);
        }
    }

    public Long id() { return id; }

    public long studentId() { return studentId; }

    public PlateNumber plate() { return plate; }

    public String vehicleTypeCode() { return vehicleTypeCode; }

    public Instant registeredAt() { return registeredAt; }

    public VehicleStatus status() { return status; }

    public Instant deactivatedAt() { return deactivatedAt; }
}
