package com.ptit.parking.pass.application.port.out;

import java.util.List;
import java.util.Optional;

/** Port: danh mục giá vé tháng theo loại xe và các xe của sinh viên. */
public interface PassCatalog {

    record VehicleInfo(long id, String plateNumber, String vehicleTypeCode, String vehicleTypeName, boolean active) {}

    record VehiclePassPrice(String vehicleTypeCode, String vehicleTypeName, long monthlyPoints) {}

    /** Giá vé tháng (điểm) của loại xe; rỗng nếu loại xe này chưa bán vé tháng. */
    Optional<Long> price(String vehicleTypeCode);

    /** Mọi xe của sinh viên (kể cả đã ngừng sử dụng — để hiển thị vé cũ). */
    List<VehicleInfo> vehiclesOf(long studentId);

    default List<VehiclePassPrice> allPrices() {
        return List.of();
    }

    default void updatePrice(String vehicleTypeCode, long monthlyPoints) {}
}
