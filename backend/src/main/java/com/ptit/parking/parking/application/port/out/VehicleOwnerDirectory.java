package com.ptit.parking.parking.application.port.out;

import java.util.Optional;
import java.util.List;
import com.ptit.parking.student.domain.PlateNumber;

/** Port: từ biển số tra ra xe và CHỦ XE — thông tin bảo vệ cần để đối chiếu thẻ sinh viên (BV01, BV02). */
public interface VehicleOwnerDirectory {

    record VehicleOwner(long vehicleId, PlateNumber plate, String vehicleTypeCode,
                        long studentUserId, String studentCode, String fullName) {}

    
    /** Một gợi ý biển số cho bảo vệ: KHÔNG có thông tin chủ xe — muốn xem chủ xe phải tra cứu (preview). */
    record PlateSuggestion(PlateNumber plate, String vehicleTypeCode, boolean parked) {}
    /** Chỉ đọc, không khóa — dùng cho các API tra cứu (preview). */
    Optional<VehicleOwner> findByPlate(PlateNumber plate);

    /**
     * Như {@link #findByPlate} nhưng KHÓA dòng xe (SELECT ... FOR UPDATE) tới hết transaction: hai bảo vệ
     * xác nhận cùng một xe thì người sau phải chờ người trước xong. Phải gọi BÊN TRONG transaction.
     */
    Optional<VehicleOwner> lockByPlate(PlateNumber plate);
    
    /** Xe và chủ xe theo id xe — khi xe ra, từ lượt gửi tìm ra ví của ai cần trừ phí. Chỉ đọc, không khóa. */
    Optional<VehicleOwner> findByVehicleId(long vehicleId);

    
    /**
     * Biển số CHỨA {@code fragment} (đã chuẩn hóa); biển KẾT THÚC bằng {@code fragment} xếp trước, rồi theo biển số.
     * {@code parkedOnly}: chỉ xe đang có lượt gửi chưa ra. Chỉ đọc, không khóa.
     */
    List<PlateSuggestion> suggestPlates(String fragment, boolean parkedOnly, int limit);
}
