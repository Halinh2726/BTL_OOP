package com.ptit.parking.shared.application.port.out;

/**
 * Port: phát sự kiện nghiệp vụ (mẫu Observer — "chủ thể" chỉ phát, không biết ai đăng ký nghe).
 * <p>Gọi BÊN TRONG transaction của use case: bộ nghe chỉ chạy SAU KHI transaction commit, nên nghiệp vụ bị rollback
 * thì không có thông báo "ma".
 * <p>{@link #NONE} là Null Object cho unit test (giống AuditTrail.NONE).
 */
@FunctionalInterface
public interface EventPublisher {

    void publish(Object event);

    EventPublisher NONE = event -> {
    };
}
