package com.ptit.parking.manager.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Guard;
import com.ptit.parking.manager.application.usecase.ChangeGuardStatusUseCase;
import com.ptit.parking.manager.application.usecase.CreateGuardUseCase;
import com.ptit.parking.manager.application.usecase.ListGuardsUseCase;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** API #20, #21, #22 — danh sách, tạo, khóa / mở khóa tài khoản bảo vệ. */
@WebMvcTest(GuardController.class)
@Import(WebSecurityTestConfig.class)
class GuardControllerTest {

    private static final String URL = "/api/v1/manager/guards";
    private static final Guard LOCKED = new Guard(4L, "baove01", "$2a$12$hash", "Trần Văn B",
            AccountStatus.BI_KHOA, "0901234567");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateGuardUseCase createGuard;

    @MockitoBean
    private ListGuardsUseCase listGuards;

    @MockitoBean
    private ChangeGuardStatusUseCase changeGuardStatus;

    @Test
    void taoThanhCong_201_khongTraMatKhau() throws Exception {
        when(createGuard.create(new CreateGuardUseCase.Command("Ngô Văn H", "0912345678", "baove03", "MatKhau@123")))
                .thenReturn(new Guard(6L, "baove03", "$2a$12$hash", "Ngô Văn H", AccountStatus.HOAT_DONG, "0912345678"));

        mvc.perform(post(URL).with(csrf()).with(authentication(manager("GUARD_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":" Ngô Văn H ","phone":"0912345678","username":"baove03","password":"MatKhau@123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.username").value("baove03"))
                .andExpect(jsonPath("$.fullName").value("Ngô Văn H"))
                .andExpect(jsonPath("$.phone").value("0912345678"))
                .andExpect(jsonPath("$.status").value("HOAT_DONG"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duLieuSai_400_tungTruong_khongGoiUseCase() throws Exception {
        mvc.perform(post(URL).with(csrf()).with(authentication(manager("GUARD_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"","phone":"912345678","username":"BV","password":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[?(@.field == 'fullName')]").exists())
                .andExpect(jsonPath("$.fields[?(@.field == 'phone')]").exists())
                .andExpect(jsonPath("$.fields[?(@.field == 'username')]").exists())
                .andExpect(jsonPath("$.fields[?(@.field == 'password')]").exists());

        verify(createGuard, never()).create(any());
    }

    @Test
    void trungTenDangNhap_409() throws Exception {
        when(createGuard.create(any())).thenThrow(new DomainException(ErrorCode.USERNAME_EXISTS));

        mvc.perform(post(URL).with(csrf()).with(authentication(manager("GUARD_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ngô Văn H","phone":"0912345678","username":"baove03","password":"MatKhau@123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }

    @Test
    void danhSach_200_quyenGuardLockCungXemDuoc_khongTraMatKhau() throws Exception {
        when(listGuards.list()).thenReturn(List.of(LOCKED,
                new Guard(6L, "baove03", "$2a$12$hash", "Ngô Văn H", AccountStatus.HOAT_DONG, "0912345678")));

        mvc.perform(get(URL).with(authentication(manager("GUARD_LOCK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(4))
                .andExpect(jsonPath("$[0].username").value("baove01"))
                .andExpect(jsonPath("$[0].fullName").value("Trần Văn B"))
                .andExpect(jsonPath("$[0].phone").value("0901234567"))
                .andExpect(jsonPath("$[0].status").value("BI_KHOA"))
                .andExpect(jsonPath("$[1].username").value("baove03"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void khoa_200_traBaoVeSauKhiCapNhat() throws Exception {
        when(changeGuardStatus.change(new ChangeGuardStatusUseCase.Command(4L, AccountStatus.BI_KHOA)))
                .thenReturn(LOCKED);

        mvc.perform(changeStatus(4, """
                        {"status":"BI_KHOA"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.status").value("BI_KHOA"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void trangThaiLaHoacThieu_400TaiTruongStatus_khongGoiUseCase() throws Exception {
        mvc.perform(changeStatus(4, """
                        {"status":"XOA"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("status"));
        mvc.perform(changeStatus(4, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("status"));

        verifyNoInteractions(changeGuardStatus);
    }

    @Test
    void idKhongPhaiSo_400() throws Exception {
        mvc.perform(patch(URL + "/abc/status").with(csrf()).with(authentication(manager("GUARD_LOCK")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"BI_KHOA"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(changeGuardStatus);
    }

    @Test
    void khongPhaiBaoVe_404_GUARD_NOT_FOUND() throws Exception {
        when(changeGuardStatus.change(any())).thenThrow(new DomainException(ErrorCode.GUARD_NOT_FOUND));

        mvc.perform(changeStatus(1, """
                        {"status":"BI_KHOA"}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUARD_NOT_FOUND"));
    }

    @Test
    void chiCoQuyenTaoBaoVe_khongKhoaDuoc_403() throws Exception {
        mvc.perform(patch(URL + "/4/status").with(csrf()).with(authentication(manager("GUARD_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"BI_KHOA"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(changeGuardStatus);
    }

    private static MockHttpServletRequestBuilder changeStatus(long id, String body) {
        return patch(URL + "/" + id + "/status").with(csrf()).with(authentication(manager("GUARD_LOCK")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static UsernamePasswordAuthenticationToken manager(String... permissions) {
        SessionUser principal = () -> 1L;
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
}
