package com.ptit.parking.pass.presentation.web;

import com.ptit.parking.pass.application.port.out.PassCatalog.VehicleInfo;
import com.ptit.parking.pass.application.usecase.BuyPassUseCase;
import com.ptit.parking.pass.domain.MonthlyPass;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import com.ptit.parking.shared.presentation.web.IdempotencyKeyHeader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Đợt 5 — vé tháng của sinh viên (quyền PASS_BUY). Mua 1 hoặc nhiều tháng liên tiếp (không ưu đãi). */
@RestController
@RequestMapping("/api/v1/me/passes")
class MyPassController {

    record BuyRequest(@NotNull(message = "Vui lòng chọn xe") @Positive Long vehicleId,
                      @NotNull(message = "Vui lòng chọn tháng")
                      @Pattern(regexp = "\\d{4}-\\d{2}", message = "Tháng dạng yyyy-MM") String month,
                      @Min(value = 1, message = "Số tháng từ 1 đến 12")
                      @Max(value = BuyPassUseCase.MAX_MONTHS, message = "Số tháng từ 1 đến 12")
                      Integer monthsCount) {}

    record PassView(long id, long vehicleId, String plateNumber, String month, String validUntil, long price,
                    long transactionId) {}

    record OptionView(long vehicleId, String plateNumber, String vehicleTypeName, Long price, PassView current,
                      PassView next, String activeValidUntil, Integer remainingDays) {}

    record OverviewResponse(String currentMonth, String nextMonth, List<OptionView> options,
                            List<PassView> passes) {}

    record BuyResponse(PassView pass, long balanceAfter, int monthsCount, long totalPrice, String validUntil) {}

    private final BuyPassUseCase passes;

    MyPassController(BuyPassUseCase passes) {
        this.passes = passes;
    }

    @GetMapping
    OverviewResponse overview(@AuthenticationPrincipal SessionUser student) {
        BuyPassUseCase.Overview o = passes.overview(student.id());
        return new OverviewResponse(
                o.currentMonth().toString(),
                o.nextMonth().toString(),
                o.options().stream().map(opt -> new OptionView(
                        opt.vehicle().id(),
                        opt.vehicle().plateNumber(),
                        opt.vehicle().vehicleTypeName(),
                        opt.price(),
                        view(opt.current(), opt.vehicle()),
                        view(opt.next(), opt.vehicle()),
                        opt.activeValidUntil() == null ? null : opt.activeValidUntil().toString(),
                        opt.remainingDays())).toList(),
                o.passes().stream().map(owned -> view(owned.pass(), owned.vehicle())).toList());
    }

    @PostMapping
    ResponseEntity<BuyResponse> buy(@AuthenticationPrincipal SessionUser student,
                                    @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String key,
                                    @Valid @RequestBody BuyRequest body) {
        int monthsCount = body.monthsCount() == null ? 1 : body.monthsCount();
        BuyPassUseCase.Purchase purchase = passes.buy(new BuyPassUseCase.Command(
                student.id(),
                IdempotencyKeyHeader.parse(key),
                body.vehicleId(),
                YearMonth.parse(body.month()),
                monthsCount));
        MonthlyPass first = purchase.pass();
        PassView passView = first == null ? null : new PassView(
                first.id(),
                first.vehicleId(),
                purchase.vehicle() == null ? null : purchase.vehicle().plateNumber(),
                first.month().toString(),
                purchase.validUntil().toString(),
                purchase.totalPrice(),
                first.transactionId());
        return IdempotencyKeyHeader.created(new BuyResponse(
                passView,
                purchase.balanceAfter(),
                purchase.monthsCount(),
                purchase.totalPrice(),
                purchase.validUntil().toString()), purchase.replayed());
    }

    private static PassView view(MonthlyPass pass, VehicleInfo vehicle) {
        return pass == null ? null : new PassView(
                pass.id(),
                pass.vehicleId(),
                vehicle == null ? null : vehicle.plateNumber(),
                pass.month().toString(),
                pass.validUntil().toString(),
                pass.pricePoints(),
                pass.transactionId());
    }
}
