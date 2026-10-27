package com.ptit.parking.student.application.port.out;

import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;
import java.util.List;

/** Port: lưu và tra cứu xe của sinh viên (bảng vehicles). */
public interface VehicleRepository {

    boolean plateExists(PlateNumber plate);

    /** Lưu xe CHƯA có id; trả về xe đã có id do CSDL sinh. */
    Vehicle save(Vehicle vehicle);

    /** Mọi xe của MỘT sinh viên (không bao giờ lẫn xe người khác). */
    List<Vehicle> findByStudentId(long studentId);
}