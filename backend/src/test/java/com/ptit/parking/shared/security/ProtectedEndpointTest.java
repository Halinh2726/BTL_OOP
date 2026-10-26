package com.ptit.parking.shared.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kiểm tra TỪNG dòng của ma trận quyền (Đặc tả API mục 1, cột Quyền) — dữ liệu test lấy từ chính
 * {@link ProtectedEndpoint}. Controller giả trả 200 cho mọi đường dẫn: request nào qua được lớp bảo mật
 * thì nhận 200, bị chặn thì nhận 403.
 */
@WebMvcTest(controllers = ProtectedEndpointTest.AnyApi.class)
@Import({ProtectedEndpointTest.AnyApi.class, WebSecurityTestConfig.class})
class ProtectedEndpointTest {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @EnumSource(ProtectedEndpoint.class)
    void coDungQuyen_thiDuocGoi(ProtectedEndpoint endpoint) throws Exception {
        for (Permission allowed : endpoint.anyOf()) {
            mvc.perform(call(endpoint).with(authentication(userWith(allowed))))
                    .andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @EnumSource(ProtectedEndpoint.class)
    void thieuQuyen_bi403Forbidden(ProtectedEndpoint endpoint) throws Exception {
        mvc.perform(call(endpoint).with(authentication(userWith(endpoint.anyOtherPermission()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @EnumSource(ProtectedEndpoint.class)
    void chuaDangNhap_bi401(ProtectedEndpoint endpoint) throws Exception {
        mvc.perform(call(endpoint))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void sinhVienKhongTheDoiDiem_baoVeKhongTheXemViCuaChuTaiKhoan() throws Exception {
        // Kịch bản nghiệp vụ cụ thể: R06 (bảo vệ không chạm vào điểm), R09 (chỉ quản lý rút điểm)
        mvc.perform(call(ProtectedEndpoint.DEPOSIT).with(authentication(userWith(
                        Permission.VEHICLE_REGISTER, Permission.WALLET_VIEW_OWN))))
                .andExpect(status().isForbidden());
        mvc.perform(call(ProtectedEndpoint.OWNER_WALLET).with(authentication(userWith(
                        Permission.PARKING_CHECK_IN, Permission.PARKING_CHECK_OUT))))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder call(ProtectedEndpoint endpoint) {
        return request(endpoint.method(), endpoint.samplePath()).with(csrf());
    }

    private static UsernamePasswordAuthenticationToken userWith(Permission... permissions) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(permissions)
                .map(p -> new SimpleGrantedAuthority(p.name())).toList();
        SessionUser principal = () -> 1L;
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    /** Controller giả: mọi đường dẫn /api/v1/** đều trả 200. */
    @RestController
    static class AnyApi {

        @RequestMapping("/api/v1/**")
        void any() {
        }
    }
}