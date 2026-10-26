package com.ptit.parking.auth.adapter.in.web;

import com.ptit.parking.auth.application.AuthenticatedUser;
import java.util.List;

/** Response của POST /auth/login và GET /me (Đặc tả API #1, #3). Không bao giờ chứa mật khẩu hay hash. */
record MeResponse(UserView user, List<String> permissions) {

    record UserView(long id, String username, String fullName, String role) {}

    static MeResponse from(AuthenticatedUser user) {
        return new MeResponse(
                new UserView(user.id(), user.username(), user.fullName(), user.role().name()),
                user.permissions());
    }
}