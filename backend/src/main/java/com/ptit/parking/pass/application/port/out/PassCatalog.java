package com.ptit.parking.pass.application.port.out;

import java.util.List;
import java.util.Optional;

/** Port CHỈ ĐỌC: giá vé tháng theo loại xe (bảng pass_prices) và các xe của sinh viên. */
public interface PassCatalog {

    record VehicleInfo(long id, String plateNumber, String vehicleTypeCode, String vehicleTypeName, boolean active) {}

    /** Giá vé tháng (điểm) của loại xe; rỗng nếu loại xe này chưa bán vé tháng. */
    Optional<Long> price(String vehicleTypeCode);

    /** Mọi xe của sinh viên (kể cả đã ngừng sử dụng — để hiển thị vé cũ). */
    List<VehicleInfo> vehiclesOf(long studentId);
}
