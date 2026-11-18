package com.ptit.parking.student.adapter.out.persistence;

import com.ptit.parking.student.application.port.out.BicycleCodeIssuer;
import com.ptit.parking.student.domain.PlateNumber;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Cài đặt {@link BicycleCodeIssuer}: bộ đếm 1 dòng vehicle_code_sequence, khóa bằng FOR UPDATE nên hai sinh viên
 * đăng ký xe đạp cùng lúc không bao giờ nhận cùng một mã. Transaction rollback thì bộ đếm cũng lùi lại.
 */
@Component
class BicycleCodeJdbcAdapter implements BicycleCodeIssuer {

    private final JdbcClient jdbc;

    BicycleCodeJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PlateNumber nextCode() {
        int next = jdbc.sql("SELECT next_value FROM vehicle_code_sequence WHERE id = 1 FOR UPDATE")
                .query(Integer.class)
                .single();
        jdbc.sql("UPDATE vehicle_code_sequence SET next_value = next_value + 1 WHERE id = 1").update();
        return PlateNumber.bicycleCode(next);
    }
}
