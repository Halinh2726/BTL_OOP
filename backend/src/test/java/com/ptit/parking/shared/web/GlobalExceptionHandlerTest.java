package com.ptit.parking.shared.web;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.hamcrest.Matchers.matchesPattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.WebSecurityTestConfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Kiểm tra định dạng lỗi chuẩn bằng một controller GIẢ chỉ dùng trong test,
 * cố tình ném đủ các loại lỗi. Chỉ khởi động tầng web, không cần CSDL.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.FakeController.class)
@Import({GlobalExceptionHandlerTest.FakeController.class,       // class lồng trong test không được tự quét
        WebSecurityTestConfig.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void loiNghiepVu_traVeMaLoiVaHttpStatusCuaErrorCode() throws Exception {
        mvc.perform(get("/test/insufficient"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINTS"))
                .andExpect(jsonPath("$.message").value("Không đủ điểm."))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.fields").doesNotExist());     // fields chỉ có với VALIDATION_ERROR
    }

    @Test
    void bodySaiRangBuoc_traVeValidationErrorKemTungTruong() throws Exception {
        mvc.perform(post("/test/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\": \"ab\", \"vehicleTypeCode\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.length()").value(2))
                .andExpect(jsonPath("$.fields[?(@.field == 'plateNumber')].code").value("PATTERN"))
                .andExpect(jsonPath("$.fields[?(@.field == 'vehicleTypeCode')].code").value("REQUIRED"));
    }

    @Test
    void thamSoQuerySaiKhoang_traVeRange() throws Exception {
        mvc.perform(get("/test/transactions").param("limit", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[0].field").value("limit"))
                .andExpect(jsonPath("$.fields[0].code").value("RANGE"));
    }

    @Test
    void jsonHong_traVeValidationError() throws Exception {
        mvc.perform(post("/test/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{khong phai json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void duongDanKhongTonTai_traVeNotFound() throws Exception {
        mvc.perform(get("/test/khong-co"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void trungBienSoTrongCsdl_dichTheoTenRangBuoc() throws Exception {
        mvc.perform(get("/test/duplicate-plate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATE_EXISTS"));
    }

    @Test
    void loaiXeKhongTonTai_traVeValidationErrorTaiTruongVehicleTypeCode() throws Exception {
        mvc.perform(get("/test/unknown-type"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("vehicleTypeCode"))
                .andExpect(jsonPath("$.fields[0].code").value("NOT_FOUND"));
    }

    @Test
    void loiKhongLuongTruoc_khongLoChiTiet() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Có lỗi xảy ra. Vui lòng thử lại."));
    }

    @Test
    void traceId_dungLaiTuHeaderNeuHopLe_khongThiTuSinh() throws Exception {
        mvc.perform(get("/test/insufficient").header(TraceIdFilter.HEADER, "abc123def4567890"))
                .andExpect(header().string(TraceIdFilter.HEADER, "abc123def4567890"))
                .andExpect(jsonPath("$.traceId").value("abc123def4567890"));

        mvc.perform(get("/test/insufficient").header(TraceIdFilter.HEADER, "bad\nvalue"))
                .andExpect(header().string(TraceIdFilter.HEADER, matchesPattern("^[0-9a-f]{16}$")));
    }

    // ----- Controller giả chỉ dùng cho test -----

    record VehicleBody(@Pattern(regexp = "^[A-Z0-9]{5,20}$") String plateNumber,
                       @NotBlank String vehicleTypeCode) {}

    @RestController
    static class FakeController {

        @GetMapping("/test/insufficient")
        void insufficient() {
            throw new DomainException(ErrorCode.INSUFFICIENT_POINTS);
        }

        @PostMapping("/test/vehicles")
        void vehicles(@Valid @RequestBody VehicleBody body) {}

        @GetMapping("/test/transactions")
        void transactions(@RequestParam @Min(1) @Max(50) int limit) {}

        @GetMapping("/test/duplicate-plate")
        void duplicatePlate() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLIntegrityConstraintViolationException(
                            "Duplicate entry '29A12345' for key 'vehicles.uq_vehicles_plate'"));
        }

        @GetMapping("/test/unknown-type")
        void unknownType() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLIntegrityConstraintViolationException(
                            "Cannot add or update a child row: a foreign key constraint fails "
                                    + "(`parking`.`vehicles`, CONSTRAINT `fk_vehicles_type` FOREIGN KEY ...)"));
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("chi tiết nội bộ không được lộ ra");
        }
    }
}