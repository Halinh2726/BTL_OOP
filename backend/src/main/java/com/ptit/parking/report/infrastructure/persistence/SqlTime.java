package com.ptit.parking.report.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** CSDL lưu DATETIME theo giờ UTC (connectionTimeZone=UTC): đổi qua lại với {@link Instant}. */
final class SqlTime {

    private SqlTime() {
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** Độ lệch múi giờ dạng "+07:00" cho CONVERT_TZ (không cần bảng múi giờ của MySQL). */
    static String offset(ZoneId zone, Instant at) {
        return zone.getRules().getOffset(at).getId().replace("Z", "+00:00");
    }
}
