package com.ptit.parking.parking.adapter.in.web;

import com.ptit.parking.parking.application.port.out.ParkingPolicyRepository;
import com.ptit.parking.parking.domain.ParkingPolicyConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cấu hình giờ khóa lấy xe (mặc định 20:00) và mức phí qua đêm (mặc định 15.000 điểm):
 * <ul>
 *   <li>{@code GET /api/v1/config/parking-policy}: mọi vai trò đã đăng nhập đều xem được (hiển thị cho SV/BV).</li>
 *   <li>{@code GET/PUT /api/v1/manager/parking-policy}: Quản lý (quyền CASH_DEPOSIT) cấu hình trực tiếp.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
class ParkingPolicyController {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    record ParkingPolicyResponse(String checkoutCurfewTime, long overnightFeePoints, boolean curfewEnabled) {
        static ParkingPolicyResponse from(ParkingPolicyConfig config) {
            return new ParkingPolicyResponse(
                    config.checkoutCurfewTime().format(HH_MM),
                    config.overnightFeePoints(),
                    config.curfewEnabled());
        }
    }

    record UpdateParkingPolicyRequest(
            @NotBlank(message = "Vui lòng nhập giờ khóa lấy xe")
            @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ khóa có dạng HH:mm (VD: 20:00)")
            String checkoutCurfewTime,

            @NotNull(message = "Vui lòng nhập mức phí qua đêm")
            @Min(value = 1000, message = "Phí qua đêm tối thiểu 1.000 điểm")
            @Max(value = 1000000, message = "Phí qua đêm tối đa 1.000.000 điểm")
            Long overnightFeePoints,

            @NotNull(message = "Vui lòng chọn trạng thái bật/tắt giờ khóa")
            Boolean curfewEnabled
    ) {}

    private final ParkingPolicyRepository repository;

    ParkingPolicyController(ParkingPolicyRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/config/parking-policy")
    ParkingPolicyResponse publicPolicy() {
        return ParkingPolicyResponse.from(repository.current());
    }

    @GetMapping("/manager/parking-policy")
    ParkingPolicyResponse managerView() {
        return ParkingPolicyResponse.from(repository.current());
    }

    @PutMapping("/manager/parking-policy")
    ParkingPolicyResponse update(@Valid @RequestBody UpdateParkingPolicyRequest body) {
        ParkingPolicyConfig updated = repository.save(new ParkingPolicyConfig(
                LocalTime.parse(body.checkoutCurfewTime(), HH_MM),
                body.overnightFeePoints(),
                body.curfewEnabled()));
        return ParkingPolicyResponse.from(updated);
    }
}
