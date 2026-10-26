package com.ptit.parking.catalog.domain;

/**
 * Loại xe được phép gửi (bảng vehicle_types), ví dụ XE_MAY - "Xe máy".
 * Value object (record bất biến): hai loại xe cùng mã và tên là MỘT, không có vòng đời riêng.
 */
public record VehicleType(String code, String name) {

    public VehicleType {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Loại xe phải có mã và tên");
        }
    }
}