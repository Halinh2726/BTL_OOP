package com.ptit.parking.catalog.application.port.out;

import java.util.List;

import com.ptit.parking.catalog.domain.VehicleType;

/** Port: đọc danh mục loại xe. Sau này module đăng ký xe dùng lại port này để kiểm tra mã loại xe. */
public interface VehicleTypeRepository {

    List<VehicleType> allVehicleTypes();
}