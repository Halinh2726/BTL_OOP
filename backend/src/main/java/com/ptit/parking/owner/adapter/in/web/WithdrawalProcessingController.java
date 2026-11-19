package com.ptit.parking.owner.adapter.in.web;

import com.ptit.parking.owner.application.port.out.OwnerFinanceQuery;
import com.ptit.parking.owner.application.usecase.ProcessWithdrawalUseCase;
import com.ptit.parking.owner.domain.WithdrawalRequest;
import com.ptit.parking.owner.domain.WithdrawalStatus;
import com.ptit.parking.shared.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý xử lý yêu cầu rút điểm (quyền CASH_WITHDRAWAL): danh sách + quỹ tiền mặt hiện có, trả tiền mặt,
 * từ chối, giải quyết khiếu nại. Quản lý KHÔNG tự tạo được yêu cầu rút (không có API).
 */
@RestController
@RequestMapping("/api/v1/manager/withdrawal-requests")
class WithdrawalProcessingController {

    record ListResponse(String ownerName, long cashHeld, List<WithdrawalView> items) {}

    record PayResponse(WithdrawalView request, boolean replayed) {}

    record NoteRequest(
            @NotBlank(message = "Vui lòng ghi lý do / kết quả")
            @Size(max = 500, message = "Tối đa 500 ký tự")
            String note) {
    }

    private final ProcessWithdrawalUseCase processing;
    private final OwnerFinanceQuery finance;
    private final ZoneId zone;

    WithdrawalProcessingController(ProcessWithdrawalUseCase processing, OwnerFinanceQuery finance, Clock clock) {
        this.processing = processing;
        this.finance = finance;
        this.zone = clock.getZone();
    }

    @GetMapping
    ListResponse list(@RequestParam(required = false) WithdrawalStatus status) {
        return new ListResponse(finance.receivingAccount().ownerName(), processing.cashHeldByManager(),
                views(processing.list(status)));
    }

    /** Đã trả tiền mặt: điểm bị trừ lúc này. Bấm lại thì replayed = true, không trừ lần hai (R17). */
    @PostMapping("/{id}/pay")
    PayResponse pay(@AuthenticationPrincipal SessionUser manager, @PathVariable long id) {
        ProcessWithdrawalUseCase.PaidResult result = processing.pay(manager.id(), id);
        return new PayResponse(views(List.of(result.request())).get(0), result.replayed());
    }

    @PostMapping("/{id}/reject")
    WithdrawalView reject(@AuthenticationPrincipal SessionUser manager, @PathVariable long id,
                          @Valid @RequestBody NoteRequest body) {
        return views(List.of(processing.reject(manager.id(), id, body.note()))).get(0);
    }

    @PostMapping("/{id}/resolve")
    WithdrawalView resolve(@AuthenticationPrincipal SessionUser manager, @PathVariable long id,
                           @Valid @RequestBody NoteRequest body) {
        return views(List.of(processing.resolveComplaint(manager.id(), id, body.note()))).get(0);
    }

    private List<WithdrawalView> views(List<WithdrawalRequest> requests) {
        Set<Long> people = new HashSet<>();
        requests.forEach(r -> {
            if (r.processedBy() != null) people.add(r.processedBy());
            if (r.resolvedBy() != null) people.add(r.resolvedBy());
        });
        var names = finance.namesOf(people);
        return requests.stream().map(r -> WithdrawalView.of(r, names, zone)).toList();
    }
}
