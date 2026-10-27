package com.ptit.parking.student.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Xe của sinh viên (SV02). Mỗi xe thuộc đúng một sinh viên; biển số là {@link PlateNumber} nên luôn
 * đã chuẩn hóa và hợp lệ. Không có setter: xe đã đăng ký thì không đổi chủ, không đổi biển.
 */
public final class Vehicle {

    private final Long id;                  // null khi chưa lưu CSDL
    private final long studentId;
    private final PlateNumber plate;
    private final String vehicleTypeCode;
    private final Instant registeredAt;

    public Vehicle(Long id, long studentId, PlateNumber plate, String vehicleTypeCode, Instant registeredAt) {
        this.id = id;
        this.studentId = studentId;
        this.plate = Objects.requireNonNull(plate, "plate");
        this.vehicleTypeCode = Objects.requireNonNull(vehicleTypeCode, "vehicleTypeCode");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
    }

    /**
     * SV02: sinh viên đăng ký xe mới. Thời điểm cắt tới micro giây cho khớp độ chính xác DATETIME(6)
     * của CSDL — giá trị trả về cho người dùng và giá trị đọc lại sau này là MỘT.
     */
    public static Vehicle register(long studentId, PlateNumber plate, String vehicleTypeCode, Instant now) {
        return new Vehicle(null, studentId, plate, vehicleTypeCode, now.truncatedTo(ChronoUnit.MICROS));
    }

    /** Bản sao đã có id do CSDL sinh (đối tượng bất biến nên không gán id vào chính nó). */
    public Vehicle withId(long newId) {
        return new Vehicle(newId, studentId, plate, vehicleTypeCode, registeredAt);
    }

    public Long id() { return id; }

    public long studentId() { return studentId; }

    public PlateNumber plate() { return plate; }

    public String vehicleTypeCode() { return vehicleTypeCode; }

    public Instant registeredAt() { return registeredAt; }
}