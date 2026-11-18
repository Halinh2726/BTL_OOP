package com.ptit.parking.account.adapter.in.web;

import com.ptit.parking.account.domain.AccountProfile;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.account.domain.PhoneNumber;
import com.ptit.parking.auth.domain.Role;

/** Thông tin cá nhân trả cho giao diện. studentCode chỉ có với sinh viên; phone null nếu chưa khai/không lưu. */
record ProfileResponse(long userId, String username, String role, String fullName, String email, String phone,
                       String studentCode, boolean phoneEditable, boolean phoneRequired) {

    static ProfileResponse from(AccountProfile profile) {
        boolean phoneEditable = switch (profile.role()) {
            case SINH_VIEN, BAO_VE -> true;
            case QUAN_LY, CHU_TAI_KHOAN -> false;
        };
        return new ProfileResponse(profile.userId(), profile.username(), profile.role().name(), profile.fullName(),
                profile.email().map(EmailAddress::value).orElse(null),
                profile.phone().map(PhoneNumber::value).orElse(null),
                profile.studentCode().orElse(null),
                phoneEditable, profile.role() == Role.BAO_VE);
    }
}
