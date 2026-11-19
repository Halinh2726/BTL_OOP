package com.ptit.parking.gate.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Sự cố tại bãi xe do bảo vệ báo. Vòng đời (đóng gói — chỉ đổi qua các phương thức nghiệp vụ):
 * <pre>
 *   report() ──▶ MOI ──startHandling()──▶ DANG_XU_LY ──resolve()──▶ DA_XU_LY
 *                 └────────────────────────────┴───────reject()───▶ TU_CHOI
 * </pre>
 * Bảo vệ chỉ BÁO (Đợt 2); quản lý nhận và xử lý (Đợt 4). Sự cố chưa đóng được đưa vào biên bản bàn giao ca.
 */
public final class Incident {

    public static final int MAX_TEXT = 1000;

    private final Long id;
    private final long reporterId;
    private final IncidentType type;
    private final PlateNumber plate;            // null: sự cố không gắn với xe cụ thể
    private final String description;
    private final Instant createdAt;
    private final boolean hasPhoto;
    private IncidentStatus status;
    private Long handledBy;
    private Instant handledAt;
    private String resolution;

    private Incident(Long id, long reporterId, IncidentType type, PlateNumber plate, String description,
                     Instant createdAt, boolean hasPhoto, IncidentStatus status, Long handledBy, Instant handledAt,
                     String resolution) {
        this.id = id;
        this.reporterId = reporterId;
        this.type = Objects.requireNonNull(type, "type");
        this.plate = plate;
        this.description = requireText(description);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.hasPhoto = hasPhoto;
        this.status = Objects.requireNonNull(status, "status");
        this.handledBy = handledBy;
        this.handledAt = handledAt;
        this.resolution = resolution;
    }

    public static Incident report(long reporterId, IncidentType type, PlateNumber plate, String description,
                                  boolean hasPhoto, Instant now) {
        return new Incident(null, reporterId, type, plate, description, now.truncatedTo(ChronoUnit.MICROS),
                hasPhoto, IncidentStatus.MOI, null, null, null);
    }

    public static Incident restore(long id, long reporterId, IncidentType type, PlateNumber plate, String description,
                                   Instant createdAt, boolean hasPhoto, IncidentStatus status, Long handledBy,
                                   Instant handledAt, String resolution) {
        return new Incident(id, reporterId, type, plate, description, createdAt, hasPhoto, status, handledBy,
                handledAt, resolution);
    }

    /** Quản lý nhận xử lý (Đợt 4). */
    public void startHandling(long managerId, Instant now) {
        if (status != IncidentStatus.MOI) {
            throw new DomainException(ErrorCode.INCIDENT_STATUS_CONFLICT);
        }
        status = IncidentStatus.DANG_XU_LY;
        handledBy = managerId;
        handledAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    /** Đã xử lý xong, bắt buộc ghi kết quả. */
    public void resolve(long managerId, String result, Instant now) {
        close(IncidentStatus.DA_XU_LY, managerId, result, now);
    }

    /** Không phải sự cố (báo nhầm...), bắt buộc ghi lý do. */
    public void reject(long managerId, String reason, Instant now) {
        close(IncidentStatus.TU_CHOI, managerId, reason, now);
    }

    public Incident withId(long newId) {
        return new Incident(newId, reporterId, type, plate, description, createdAt, hasPhoto, status, handledBy,
                handledAt, resolution);
    }

    public boolean isOpen() {
        return !status.isClosed();
    }

    public boolean reportedBy(long userId) {
        return reporterId == userId;
    }

    private void close(IncidentStatus closed, long managerId, String text, Instant now) {
        if (status.isClosed()) {
            throw new DomainException(ErrorCode.INCIDENT_STATUS_CONFLICT);
        }
        resolution = requireText(text);
        status = closed;
        handledBy = managerId;
        handledAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    private static String requireText(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > MAX_TEXT) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        return text;
    }

    public Long id() { return id; }

    public long reporterId() { return reporterId; }

    public IncidentType type() { return type; }

    public Optional<PlateNumber> plate() { return Optional.ofNullable(plate); }

    public String description() { return description; }

    public Instant createdAt() { return createdAt; }

    public boolean hasPhoto() { return hasPhoto; }

    public IncidentStatus status() { return status; }

    public Long handledBy() { return handledBy; }

    public Instant handledAt() { return handledAt; }

    public String resolution() { return resolution; }
}
