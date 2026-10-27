package com.ptit.parking.parking.application.port.out;

import java.util.Optional;

import com.ptit.parking.student.domain.PlateNumber;

/** Port: từ biển số tra ra xe và CHỦ XE — thông tin bảo vệ cần để đối chiếu thẻ sinh viên (BV01, BV02). */
public interface VehicleOwnerDirectory {

    record VehicleOwner(long vehicleId, PlateNumber plate, String vehicleTypeCode,
                        long studentUserId, String studentCode, String fullName) {}

    /** Chỉ đọc, không khóa — dùng cho các API tra cứu (preview). */
    Optional<VehicleOwner> findByPlate(PlateNumber plate);

    /**
     * Như {@link #findByPlate} nhưng KHÓA dòng xe (SELECT ... FOR UPDATE) tới hết transaction: hai bảo vệ
     * xác nhận cùng một xe thì người sau phải chờ người trước xong. Phải gọi BÊN TRONG transaction.
     */
    Optional<VehicleOwner> lockByPlate(PlateNumber plate);
}
