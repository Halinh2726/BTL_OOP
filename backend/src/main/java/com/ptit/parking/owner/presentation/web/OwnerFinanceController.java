package com.ptit.parking.owner.presentation.web;

import com.ptit.parking.owner.application.port.out.OwnerFinanceQuery;
import com.ptit.parking.owner.application.port.out.OwnerFinanceQuery.DailyRevenue;
import com.ptit.parking.owner.application.usecase.OwnerFinanceUseCase;
import com.ptit.parking.owner.application.usecase.OwnerWithdrawalUseCase;
import com.ptit.parking.owner.domain.WithdrawalRequest;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chủ bãi xe (quyền OWNER_FINANCE): tổng quan, doanh thu theo ngày, yêu cầu rút điểm của chính mình.
 * Chủ bãi xe KHÔNG có API nào sửa giao dịch, đổi điểm cho sinh viên hay tự duyệt yêu cầu rút.
 */
@RestController
@RequestMapping("/api/v1/owner")
class OwnerFinanceController {

    record CreateWithdrawalRequest(
            @NotNull(message = "Vui lòng nhập số điểm")
            @Positive(message = "Số điểm phải lớn hơn 0")
            @Max(value = WithdrawalRequest.MAX_POINTS, message = "Tối đa 10.000.000 điểm mỗi yêu cầu")
            Long points) {
    }

    record ComplaintRequest(
            @NotBlank(message = "Vui lòng ghi rõ chưa nhận đủ bao nhiêu, vì sao")
            @Size(max = 500, message = "Tối đa 500 ký tự")
            String note) {
    }

    record RevenueResponse(String from, String to, List<DailyRevenue> days, long totalPayments, long totalRevenue,
                           long totalAdjustments, long totalCashIn, long totalCashOut) {}

    private final OwnerFinanceUseCase finance;
    private final OwnerWithdrawalUseCase withdrawals;
    private final OwnerFinanceQuery names;
    private final ZoneId zone;

    OwnerFinanceController(OwnerFinanceUseCase finance, OwnerWithdrawalUseCase withdrawals, OwnerFinanceQuery names,
                           Clock clock) {
        this.finance = finance;
        this.withdrawals = withdrawals;
        this.names = names;
        this.zone = clock.getZone();
    }

    @GetMapping("/overview")
    OwnerFinanceUseCase.Overview overview() {
        return finance.overview();
    }

    @GetMapping("/revenue")
    RevenueResponse revenue(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<DailyRevenue> days = finance.daily(from, to);
        return new RevenueResponse(from.toString(), to.toString(), days,
                days.stream().mapToLong(DailyRevenue::payments).sum(),
                days.stream().mapToLong(DailyRevenue::revenue).sum(),
                days.stream().mapToLong(DailyRevenue::adjustments).sum(),
                days.stream().mapToLong(DailyRevenue::cashIn).sum(),
                days.stream().mapToLong(DailyRevenue::cashOut).sum());
    }

    @GetMapping("/withdrawal-requests")
    List<WithdrawalView> myRequests(@AuthenticationPrincipal SessionUser owner) {
        return views(withdrawals.mine(owner.id()));
    }

    @PostMapping("/withdrawal-requests")
    @ResponseStatus(HttpStatus.CREATED)
    WithdrawalView create(@AuthenticationPrincipal SessionUser owner, @Valid @RequestBody CreateWithdrawalRequest body) {
        return view(withdrawals.create(owner.id(), body.points()));
    }

    @PostMapping("/withdrawal-requests/{id}/cancel")
    WithdrawalView cancel(@AuthenticationPrincipal SessionUser owner, @PathVariable long id) {
        return view(withdrawals.cancel(owner.id(), id));
    }

    @PostMapping("/withdrawal-requests/{id}/confirm")
    WithdrawalView confirm(@AuthenticationPrincipal SessionUser owner, @PathVariable long id) {
        return view(withdrawals.confirmReceived(owner.id(), id));
    }

    @PostMapping("/withdrawal-requests/{id}/complain")
    WithdrawalView complain(@AuthenticationPrincipal SessionUser owner, @PathVariable long id,
                            @Valid @RequestBody ComplaintRequest body) {
        return view(withdrawals.complain(owner.id(), id, body.note()));
    }

    private WithdrawalView view(WithdrawalRequest request) {
        return views(List.of(request)).get(0);
    }

    private List<WithdrawalView> views(List<WithdrawalRequest> requests) {
        Set<Long> people = new HashSet<>();
        requests.forEach(r -> {
            if (r.processedBy() != null) people.add(r.processedBy());
            if (r.resolvedBy() != null) people.add(r.resolvedBy());
        });
        var nameMap = names.namesOf(people);
        return requests.stream().map(r -> WithdrawalView.of(r, nameMap, zone)).toList();
    }
}
