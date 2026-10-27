package com.ptit.parking.student.adapter.in.web;

import com.ptit.parking.auth.domain.Student;
import com.ptit.parking.student.application.usecase.RegisterStudentUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API #4 — đăng ký tài khoản sinh viên. Công khai, không cần CSRF (chưa có phiên — xem SecurityConfig).
 * Không tự đăng nhập: giao diện chuyển về trang đăng nhập sau khi nhận 201.
 */
@RestController
@RequestMapping("/api/v1")
class StudentRegistrationController {

    private final RegisterStudentUseCase registerStudent;

    StudentRegistrationController(RegisterStudentUseCase registerStudent) {
        this.registerStudent = registerStudent;
    }

    @PostMapping("/students/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisteredStudentResponse register(@Valid @RequestBody RegisterStudentRequest body) {
        Student student = registerStudent.register(new RegisterStudentUseCase.Command(
                body.studentCode(), body.fullName(), body.username(), body.password()));
        return RegisteredStudentResponse.from(student);
    }
}