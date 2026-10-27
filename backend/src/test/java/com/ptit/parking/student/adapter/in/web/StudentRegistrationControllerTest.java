package com.ptit.parking.student.adapter.in.web;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Student;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import com.ptit.parking.student.application.usecase.RegisterStudentUseCase;
import com.ptit.parking.student.application.usecase.RegisterStudentUseCase.Command;

/** API #4: công khai, không cần CSRF; kiểm tra dữ liệu, chuẩn hóa và mã lỗi 409. */
@WebMvcTest(StudentRegistrationController.class)
@Import(WebSecurityTestConfig.class)
class StudentRegistrationControllerTest {

    private static final String URL = "/api/v1/students/register";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RegisterStudentUseCase registerStudent;

    @Test
    void hopLe_tra201_daChuanHoa_khongCanDangNhapHayCsrf() throws Exception {
        when(registerStudent.register(any())).thenAnswer(call -> {
            Command c = call.getArgument(0);
            return new Student(3L, c.username(), "hash", c.fullName(), AccountStatus.HOAT_DONG, c.studentCode());
        });

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"studentCode":" b21dccn001 ","fullName":"  Nguyễn Văn A ","username":"sv01","password":"MatKhau@123"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.username").value("sv01"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.studentCode").value("B21DCCN001"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duLieuSai_bao400_chiRaTungTruong_khongGoiUseCase() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"studentCode":"B21-001","fullName":"Nguyễn Văn A","username":"SV","password":"123"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[?(@.field == 'studentCode')]").exists())
                .andExpect(jsonPath("$.fields[?(@.field == 'username')]").exists())
                .andExpect(jsonPath("$.fields[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.fields[?(@.field == 'fullName')]").doesNotExist());

        verify(registerStudent, never()).register(any());
    }

    @Test
    void trungTenDangNhap_bao409() throws Exception {
        when(registerStudent.register(any())).thenThrow(new DomainException(ErrorCode.USERNAME_EXISTS));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"studentCode":"B21DCCN001","fullName":"Nguyễn Văn A","username":"sv01","password":"MatKhau@123"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }
}