package com.ptit.parking.student.application.port.out;

import com.ptit.parking.student.domain.PlateNumber;

/** Port: cấp mã XD-xxxxx tiếp theo cho xe đạp không có biển. Phải gọi BÊN TRONG transaction (khóa bộ đếm). */
public interface BicycleCodeIssuer {

    PlateNumber nextCode();
}
