package com.ptit.parking.parking.application.usecase;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.PlateSuggestion;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.util.List;
import java.util.regex.Pattern;

/**
 * BV01, BV02 bước 1 (Đặc tả API #24) — bảo vệ gõ vài ký tự (thường là mấy số cuối) và chạm chọn biển số,
 * khỏi gõ đủ cả biển. CHỈ ĐỌC. Chỉ trả biển số và loại xe: thông tin chủ xe vẫn phải qua bước tra cứu (#12, #14).
 */
public class SuggestPlatesUseCase {

    /** Đủ để chọn trên màn hình máy tính bảng; gõ thêm ký tự nếu chưa thấy xe cần tìm. */
    public static final int MAX_SUGGESTIONS = 8;

    /** Ít nhất 2 ký tự: 1 ký tự thì khớp gần như mọi xe, gợi ý không có ích. */
    private static final Pattern FRAGMENT = Pattern.compile("^[A-Z0-9]{2,20}$");

    private final VehicleOwnerDirectory vehicles;

    public SuggestPlatesUseCase(VehicleOwnerDirectory vehicles) {
        this.vehicles = vehicles;
    }

    /** @param parkedOnly true khi cho xe RA: chỉ gợi ý xe đang ở trong bãi. */
    public List<PlateSuggestion> suggest(String rawFragment, boolean parkedOnly) {
        String fragment = PlateNumber.normalize(rawFragment);           // cùng quy tắc với biển số: bỏ " . -", viết hoa
        if (fragment == null || !FRAGMENT.matcher(fragment).matches()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        return vehicles.suggestPlates(fragment, parkedOnly, MAX_SUGGESTIONS);
    }
}
