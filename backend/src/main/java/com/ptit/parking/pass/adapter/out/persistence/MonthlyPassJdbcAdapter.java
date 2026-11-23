package com.ptit.parking.pass.adapter.out.persistence;

import com.ptit.parking.parking.application.port.out.PassCoverage;
import com.ptit.parking.pass.application.port.out.MonthlyPassRepository;
import com.ptit.parking.pass.application.port.out.PassCatalog;
import com.ptit.parking.pass.domain.MonthlyPass;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/**
 * Cài đặt các port của module pass trên monthly_passes / pass_prices / vehicles — và CẢ port
 * {@link PassCoverage} mà module parking cần.
 */
@Component
class MonthlyPassJdbcAdapter implements MonthlyPassRepository, PassCatalog, PassCoverage {

    private static final String SELECT =
            "SELECT id, student_id, vehicle_id, valid_month, price_points, transaction_id, purchased_at FROM monthly_passes ";

    private final JdbcClient jdbc;

    MonthlyPassJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ===== MonthlyPassRepository =====

    @Override
    public MonthlyPass insert(MonthlyPass pass) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO monthly_passes (student_id, vehicle_id, valid_month, price_points, transaction_id, purchased_at)
                VALUES (:student, :vehicle, :month, :price, :tx, :at)""")
                .param("student", pass.studentId())
                .param("vehicle", pass.vehicleId())
                .param("month", pass.month().atDay(1))
                .param("price", pass.pricePoints())
                .param("tx", pass.transactionId())
                .param("at", LocalDateTime.ofInstant(pass.purchasedAt(), ZoneOffset.UTC))
                .update(keys);
        return pass.withId(keys.getKeyAs(Number.class).longValue());
    }

    @Override
    public Optional<MonthlyPass> findFor(long vehicleId, YearMonth month) {
        return jdbc.sql(SELECT + "WHERE vehicle_id = :vehicle AND valid_month = :month")
                .param("vehicle", vehicleId).param("month", month.atDay(1))
                .query(MonthlyPassJdbcAdapter::map).optional();
    }

    @Override
    public List<MonthlyPass> findByStudent(long studentId) {
        return jdbc.sql(SELECT + "WHERE student_id = :student ORDER BY valid_month DESC, id DESC")
                .param("student", studentId).query(MonthlyPassJdbcAdapter::map).list();
    }

    @Override
    public List<MonthlyPass> findByTransactionId(long transactionId) {
        return jdbc.sql(SELECT + "WHERE transaction_id = :tx ORDER BY valid_month ASC")
                .param("tx", transactionId).query(MonthlyPassJdbcAdapter::map).list();
    }

    @Override
    public List<MonthlyPass> findForVehicleInMonths(long vehicleId, List<YearMonth> months) {
        if (months.isEmpty()) {
            return List.of();
        }
        List<LocalDate> days = months.stream().map(m -> m.atDay(1)).toList();
        return jdbc.sql(SELECT + "WHERE vehicle_id = :vehicle AND valid_month IN (:months) ORDER BY valid_month ASC")
                .param("vehicle", vehicleId).param("months", days)
                .query(MonthlyPassJdbcAdapter::map).list();
    }

    // ===== PassCoverage (cho module parking) =====

    @Override
    public Optional<LocalDate> coveredUntil(long vehicleId, LocalDate day) {
        return findFor(vehicleId, YearMonth.from(day)).map(MonthlyPass::validUntil);
    }

    // ===== PassCatalog =====

    @Override
    public Optional<Long> price(String vehicleTypeCode) {
        return jdbc.sql("SELECT monthly_points FROM pass_prices WHERE vehicle_type_code = :type")
                .param("type", vehicleTypeCode).query(Long.class).optional();
    }

    @Override
    public List<VehicleInfo> vehiclesOf(long studentId) {
        return jdbc.sql("""
                SELECT v.id, v.plate_number, v.vehicle_type_code, t.name, v.status = 'DANG_DUNG' AS active
                FROM vehicles v JOIN vehicle_types t ON t.code = v.vehicle_type_code
                WHERE v.student_id = :student
                ORDER BY v.status, v.registered_at DESC""")
                .param("student", studentId)
                .query((rs, row) -> new VehicleInfo(rs.getLong("id"), rs.getString("plate_number"),
                        rs.getString("vehicle_type_code"), rs.getString("name"), rs.getBoolean("active")))
                .list();
    }

    @Override
    public List<VehiclePassPrice> allPrices() {
        return jdbc.sql("""
                SELECT p.vehicle_type_code, t.name, p.monthly_points
                FROM pass_prices p
                JOIN vehicle_types t ON t.code = p.vehicle_type_code
                ORDER BY p.monthly_points DESC, p.vehicle_type_code ASC""")
                .query((rs, row) -> new VehiclePassPrice(
                        rs.getString("vehicle_type_code"),
                        rs.getString("name"),
                        rs.getLong("monthly_points")))
                .list();
    }

    @Override
    public void updatePrice(String vehicleTypeCode, long monthlyPoints) {
        int rows = jdbc.sql("UPDATE pass_prices SET monthly_points = :points WHERE vehicle_type_code = :code")
                .param("points", monthlyPoints)
                .param("code", vehicleTypeCode)
                .update();
        if (rows == 0) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
    }

    private static MonthlyPass map(ResultSet rs, int row) throws SQLException {
        return MonthlyPass.restore(rs.getLong("id"), rs.getLong("student_id"), rs.getLong("vehicle_id"),
                YearMonth.from(rs.getObject("valid_month", LocalDate.class)), rs.getLong("price_points"),
                rs.getLong("transaction_id"),
                rs.getObject("purchased_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }
}
