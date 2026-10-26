package com.ptit.parking.shared.web;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API #23 – GET /api/v1/health: công khai, dùng cho giám sát. */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final int DB_CHECK_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    // DI qua constructor: lớp không tự new phụ thuộc của mình
    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        if (isDatabaseUp()) {
            return ResponseEntity.ok(new HealthResponse("UP", "UP"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new HealthResponse("DOWN", "DOWN"));
    }

    private boolean isDatabaseUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DB_CHECK_TIMEOUT_SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    public record HealthResponse(String status, String database) {}
}
