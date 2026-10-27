package com.ptit.parking.parking.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.student.domain.PlateNumber;

/** Cài đặt {@link VehicleOwnerDirectory}: ghép vehicles + students + users theo biển số (đã chuẩn hóa). */
@Component
class VehicleOwnerJdbcAdapter implements VehicleOwnerDirectory {

    private static final String SELECT_BY_PLATE = """
            SELECT v.id, v.plate_number, v.vehicle_type_code, u.id AS user_id, s.student_code, u.full_name
            FROM vehicles v
            JOIN students s ON s.user_id = v.student_id
            JOIN users u ON u.id = s.user_id
            WHERE v.plate_number = :plate""";

    private final JdbcClient jdbc;

    VehicleOwnerJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VehicleOwner> findByPlate(PlateNumber plate) {
        return jdbc.sql(SELECT_BY_PLATE)
                .param("plate", plate.value())
                .query(VehicleOwnerJdbcAdapter::map)
                .optional();
    }

    /** "FOR UPDATE OF v": chỉ khóa dòng vehicles (Đặc tả API mục 8), không khóa dòng sinh viên/người dùng. */
    @Override
    public Optional<VehicleOwner> lockByPlate(PlateNumber plate) {
        return jdbc.sql(SELECT_BY_PLATE + " FOR UPDATE OF v")
                .param("plate", plate.value())
                .query(VehicleOwnerJdbcAdapter::map)
                .optional();
    }

    private static VehicleOwner map(ResultSet rs, int row) throws SQLException {
        return new VehicleOwner(rs.getLong("id"), new PlateNumber(rs.getString("plate_number")),
                rs.getString("vehicle_type_code"), rs.getLong("user_id"),
                rs.getString("student_code"), rs.getString("full_name"));
    }
}
