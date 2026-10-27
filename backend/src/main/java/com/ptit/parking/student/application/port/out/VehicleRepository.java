package com.ptit.parking.student.application.port.out;

import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;

/** Port: lưu và tra cứu xe của sinh viên (bảng vehicles). */
public interface VehicleRepository {

    boolean plateExists(PlateNumber plate);

    /** Lưu xe CHƯA có id; trả về xe đã có id do CSDL sinh. */
    Vehicle save(Vehicle vehicle);
}