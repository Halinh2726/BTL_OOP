package com.ptit.parking.payment.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.manager.application.port.out.StudentDirectory;
import com.ptit.parking.manager.application.port.out.StudentDirectory.StudentAccount;
import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase;
import com.ptit.parking.payment.domain.BankAccountConfig;
import com.ptit.parking.shared.security.Permission;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Kiểm thử tầng web cho {@link StudentDepositController} (lấy thông tin nạp điểm VietQR cá nhân).
 */
@WebMvcTest(StudentDepositController.class)
@Import(WebSecurityTestConfig.class)
class StudentDepositControllerTest {

    private static final long STUDENT_ID = 3L;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StudentDirectory students;

    @MockitoBean
    private GetBankConfigUseCase getBankConfig;

    @Test
    void layThongTinNapDiem_sinhVienHopLe_traVeVietQr() throws Exception {
        when(students.findByUserId(STUDENT_ID)).thenReturn(Optional.of(
                new StudentAccount(STUDENT_ID, "B21DCCN001", "Nguyễn Văn A", 10L)));
        when(getBankConfig.currentConfig()).thenReturn(
                new BankAccountConfig("MB", "0987654321", "BAI XE HOC VIEN PTIT", "compact2"));

        mvc.perform(get("/api/v1/me/deposit-info?amount=100000")
                        .with(authentication(studentAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCode").value("B21DCCN001"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.transferContent").value("NAP B21DCCN001"))
                .andExpect(jsonPath("$.amount").value(100000))
                .andExpect(jsonPath("$.qrCodeUrl").value(org.hamcrest.Matchers.containsString("NAP%20B21DCCN001")));
    }

    private static UsernamePasswordAuthenticationToken studentAuth() {
        SessionUser user = () -> STUDENT_ID;
        return UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority(Permission.WALLET_VIEW_OWN.name())));
    }
}

