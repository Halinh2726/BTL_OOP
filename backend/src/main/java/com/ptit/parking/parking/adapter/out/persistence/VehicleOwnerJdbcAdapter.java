package com.ptit.parking.parking.adapter.out.persistence;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.student.domain.PlateNumber;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import java.util.List;
/** Cài đặt {@link VehicleOwnerDirectory}: ghép vehicles + students + users theo biển số (đã chuẩn hóa) hoặc id xe. */
@Component
class VehicleOwnerJdbcAdapter implements VehicleOwnerDirectory {

    private static final String SELECT_OWNER = """
            SELECT v.id, v.plate_number, v.vehicle_type_code, u.id AS user_id, s.student_code, u.full_name
            FROM vehicles v
            JOIN students s ON s.user_id = v.student_id
            JOIN users u ON u.id = s.user_id
            """;

    private final JdbcClient jdbc;

    VehicleOwnerJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VehicleOwner> findByPlate(PlateNumber plate) {
        return jdbc.sql(SELECT_OWNER + "WHERE v.plate_number = :plate")
                .param("plate", plate.value())
                .query(VehicleOwnerJdbcAdapter::map)
                .optional();
    }

    /** "FOR UPDATE OF v": chỉ khóa dòng vehicles (Đặc tả API mục 8), không khóa dòng sinh viên/người dùng. */
    @Override
    public Optional<VehicleOwner> lockByPlate(PlateNumber plate) {
        return jdbc.sql(SELECT_OWNER + "WHERE v.plate_number = :plate FOR UPDATE OF v")
                .param("plate", plate.value())
                .query(VehicleOwnerJdbcAdapter::map)
                .optional();
    }

    @Override
    public Optional<VehicleOwner> findByVehicleId(long vehicleId) {
        return jdbc.sql(SELECT_OWNER + "WHERE v.id = :vehicleId")
                .param("vehicleId", vehicleId)
                .query(VehicleOwnerJdbcAdapter::map)
                .optional();
    }

        /**
     * open_vehicle_id = id xe khi lượt đang gửi (cột tính toán của V005), nên LEFT JOIN theo nó cho biết xe có trong bãi.
     * fragment chỉ gồm chữ và số (use case đã kiểm tra) nên không chứa ký tự đại diện % hay _ của LIKE.
     */
    @Override
    public List<PlateSuggestion> suggestPlates(String fragment, boolean parkedOnly, int limit) {
        return jdbc.sql("""
                        SELECT v.plate_number, v.vehicle_type_code, ps.id IS NOT NULL AS parked
                        FROM vehicles v
                        LEFT JOIN parking_sessions ps ON ps.open_vehicle_id = v.id
                        WHERE v.plate_number LIKE :contains
                          AND (:parkedOnly = FALSE OR ps.id IS NOT NULL)
                        ORDER BY v.plate_number LIKE :endsWith DESC, v.plate_number
                        LIMIT :limit
                        """)
                .param("contains", "%" + fragment + "%")
                .param("endsWith", "%" + fragment)
                .param("parkedOnly", parkedOnly)
                .param("limit", limit)
                .query((rs, row) -> new PlateSuggestion(new PlateNumber(rs.getString("plate_number")),
                        rs.getString("vehicle_type_code"), rs.getBoolean("parked")))
                .list();
    }

    private static VehicleOwner map(ResultSet rs, int row) throws SQLException {
        return new VehicleOwner(rs.getLong("id"), new PlateNumber(rs.getString("plate_number")),
                rs.getString("vehicle_type_code"), rs.getLong("user_id"),
                rs.getString("student_code"), rs.getString("full_name"));
    }
}
