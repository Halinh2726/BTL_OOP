package com.ptit.parking.student.infrastructure.persistence;

import com.ptit.parking.student.application.port.out.VehicleRepository;
import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;
import com.ptit.parking.student.domain.VehicleStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Cài đặt {@link VehicleRepository} bằng SQL thuần. Thời điểm lưu theo UTC (quy ước chung của CSDL). */
@Component
class VehicleJdbcAdapter implements VehicleRepository {

    private static final String SELECT = """
            SELECT id, student_id, plate_number, vehicle_type_code, registered_at, status, deactivated_at
            FROM vehicles
            """;

    private final JdbcClient jdbc;

    VehicleJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** active_plate = biển số của xe ĐANG DÙNG (cột tính toán V011, có UNIQUE). */
    @Override
    public boolean plateExists(PlateNumber plate) {
        return jdbc.sql("SELECT COUNT(*) FROM vehicles WHERE active_plate = :plate")
                .param("plate", plate.value())
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public boolean plateExistsForUpdate(PlateNumber plate) {
        return jdbc.sql("SELECT id FROM vehicles WHERE active_plate = :plate FOR UPDATE")
                .param("plate", plate.value()).query(Long.class).optional().isPresent();
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO vehicles (student_id, plate_number, vehicle_type_code, registered_at)
                VALUES (:studentId, :plate, :type, :registeredAt)""")
                .param("studentId", vehicle.studentId())
                .param("plate", vehicle.plate().value())
                .param("type", vehicle.vehicleTypeCode())
                .param("registeredAt", LocalDateTime.ofInstant(vehicle.registeredAt(), ZoneOffset.UTC))
                .update(keys);
        return vehicle.withId(keys.getKey().longValue());
    }

    @Override
    public List<Vehicle> findByStudentId(long studentId) {
        return jdbc.sql(SELECT + "WHERE student_id = :studentId")
                .param("studentId", studentId)
                .query(VehicleJdbcAdapter::map)
                .list();
    }

    @Override
    public Optional<Vehicle> findById(long vehicleId) {
        return jdbc.sql(SELECT + "WHERE id = :id")
                .param("id", vehicleId)
                .query(VehicleJdbcAdapter::map)
                .optional();
    }

    @Override
    public long countActiveForUpdate(long studentId) {
        jdbc.sql("SELECT user_id FROM students WHERE user_id = :id FOR UPDATE")
                .param("id", studentId)
                .query(Long.class)
                .optional();
        return jdbc.sql("SELECT COUNT(*) FROM vehicles WHERE student_id = :id AND status = 'DANG_DUNG'")
                .param("id", studentId)
                .query(Long.class)
                .single();
    }

    @Override
    public void update(Vehicle vehicle) {
        jdbc.sql("""
                UPDATE vehicles SET vehicle_type_code = :type, status = :status, deactivated_at = :deactivatedAt
                WHERE id = :id""")
                .param("type", vehicle.vehicleTypeCode())
                .param("status", vehicle.status().name())
                .param("deactivatedAt", vehicle.deactivatedAt() == null ? null
                        : LocalDateTime.ofInstant(vehicle.deactivatedAt(), ZoneOffset.UTC))
                .param("id", vehicle.id())
                .update();
    }

    private static Vehicle map(ResultSet rs, int row) throws SQLException {
        LocalDateTime deactivatedAt = rs.getObject("deactivated_at", LocalDateTime.class);
        return new Vehicle(
                rs.getLong("id"),
                rs.getLong("student_id"),
                new PlateNumber(rs.getString("plate_number")),
                rs.getString("vehicle_type_code"),
                rs.getObject("registered_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                VehicleStatus.valueOf(rs.getString("status")),
                deactivatedAt == null ? null : deactivatedAt.toInstant(ZoneOffset.UTC));
    }
}
