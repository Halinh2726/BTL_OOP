package com.ptit.parking.gate.application.port.out;

import java.util.List;
import java.util.Optional;

/** Port CHỈ ĐỌC: danh sách bảo vệ để phân ca (tên, còn hoạt động hay bị khóa). */
public interface GuardRoster {

    record GuardInfo(long id, String fullName, boolean active) {}

    List<GuardInfo> all();

    Optional<GuardInfo> find(long guardId);
}
