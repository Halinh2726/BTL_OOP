package com.ptit.parking.manager.adapter.in.web;

import com.ptit.parking.auth.domain.Guard;

/** Một bảo vệ trong response của API #20, #21, #22. Không bao giờ chứa mật khẩu hay hash. */
record GuardResponse(long id, String username, String fullName, String phone, String status) {

    static GuardResponse from(Guard guard) {
        return new GuardResponse(guard.id(), guard.username(), guard.fullName(), guard.phone(), guard.status().name());
    }
}