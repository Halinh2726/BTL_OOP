package com.ptit.parking.manager.application.usecase;

import com.ptit.parking.auth.domain.Guard;
import com.ptit.parking.manager.application.port.out.GuardAccountRepository;
import java.util.List;

/** QL03, QL04 (Đặc tả API #20) — danh sách bảo vệ, kể cả người đang bị khóa, để quản lý chọn khóa / mở khóa. */
public class ListGuardsUseCase {

    private final GuardAccountRepository guards;

    public ListGuardsUseCase(GuardAccountRepository guards) {
        this.guards = guards;
    }

    public List<Guard> list() {
        return guards.findAll();
    }
}
