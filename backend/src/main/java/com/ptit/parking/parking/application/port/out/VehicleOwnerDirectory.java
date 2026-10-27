package com.ptit.parking.parking.application.port.out;

import com.ptit.parking.student.domain.PlateNumber;
import java.util.Optional;

/** Port: từ biển số tra ra xe và CHỦ XE — thông tin bảo vệ cần để đối chiếu thẻ sinh viên (BV01, BV02). */
public interface VehicleOwnerDirectory {

    record VehicleOwner(long vehicleId, PlateNumber plate, String vehicleTypeCode,
                        long studentUserId, String studentCode, String fullName) {}

    Optional<VehicleOwner> findByPlate(PlateNumber plate);
}