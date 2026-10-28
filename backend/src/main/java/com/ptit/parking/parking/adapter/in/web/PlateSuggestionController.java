package com.ptit.parking.parking.adapter.in.web;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.PlateSuggestion;
import com.ptit.parking.parking.application.usecase.SuggestPlatesUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gợi ý biển số cho màn hình xe vào/xe ra. Quyền PARKING_CHECK_IN hoặc PARKING_CHECK_OUT (ma trận ProtectedEndpoint). */
@RestController
@RequestMapping("/api/v1/guard/plates")
class PlateSuggestionController {

    /** Một dòng của response API #24. */
    record PlateSuggestionResponse(String plateNumber, String vehicleTypeCode, boolean parked) {

        static PlateSuggestionResponse from(PlateSuggestion suggestion) {
            return new PlateSuggestionResponse(suggestion.plate().value(), suggestion.vehicleTypeCode(),
                    suggestion.parked());
        }
    }

    private final SuggestPlatesUseCase suggestPlates;

    PlateSuggestionController(SuggestPlatesUseCase suggestPlates) {
        this.suggestPlates = suggestPlates;
    }

    /** API #24 — {@code ?q=2345&parked=true}: biển số chứa "2345", chỉ xe đang trong bãi. */
    @GetMapping
    List<PlateSuggestionResponse> suggest(@RequestParam String q,
                                          @RequestParam(defaultValue = "false") boolean parked) {
        return suggestPlates.suggest(q, parked).stream().map(PlateSuggestionResponse::from).toList();
    }
}
