package com.ptit.parking.student.application.port.out;

import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;
import java.util.List;
import java.util.Optional;

/** Port: lưu và tra cứu xe của sinh viên (bảng vehicles). */
public interface VehicleRepository {

    /** Biển số đang thuộc một xe ĐANG DÙNG (xe đã ngừng không giữ chỗ biển số). */
    boolean plateExists(PlateNumber plate);

    /** Lưu xe CHƯA có id; trả về xe đã có id do CSDL sinh. */
    Vehicle save(Vehicle vehicle);

    /** Mọi xe của MỘT sinh viên, kể cả xe đã ngừng (không bao giờ lẫn xe người khác). */
    List<Vehicle> findByStudentId(long studentId);

    /** Đợt 1. */
    Optional<Vehicle> findById(long vehicleId);

    /**
     * Đợt 1: số xe ĐANG DÙNG của sinh viên, sau khi KHÓA dòng sinh viên tới hết transaction — hai lần đăng ký song song
     * của cùng sinh viên phải xếp hàng, không lọt quá giới hạn 3 xe. Phải gọi BÊN TRONG transaction.
     */
    long countActiveForUpdate(long studentId);

    /** Đợt 1: ghi loại xe và trạng thái của một xe đã có id. */
    void update(Vehicle vehicle);
}
