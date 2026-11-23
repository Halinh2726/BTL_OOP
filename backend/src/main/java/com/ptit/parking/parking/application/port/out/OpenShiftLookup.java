package com.ptit.parking.parking.application.port.out;

import java.util.Optional;

/** Port nhẹ tra ca đang trực hiện tại của bảo vệ (tránh phụ thuộc chéo từ module parking sang gate). */
@FunctionalInterface
public interface OpenShiftLookup {

    Optional<Long> findOpenShiftId(long guardId);
}
