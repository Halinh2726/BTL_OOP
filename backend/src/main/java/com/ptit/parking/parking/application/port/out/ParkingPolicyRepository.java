package com.ptit.parking.parking.application.port.out;

import com.ptit.parking.parking.domain.ParkingPolicyConfig;

/** Port đọc và cập nhật cấu hình giờ khóa lấy xe & phí qua đêm trên system_config. */
public interface ParkingPolicyRepository {

    ParkingPolicyConfig current();

    ParkingPolicyConfig save(ParkingPolicyConfig config);
}
