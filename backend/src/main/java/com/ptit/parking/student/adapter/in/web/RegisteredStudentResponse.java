package com.ptit.parking.student.adapter.in.web;

import com.ptit.parking.auth.domain.Student;

/** Response 201 của POST /students/register. Không bao giờ chứa mật khẩu hay hash. */
record RegisteredStudentResponse(long id, String username, String fullName, String studentCode) {

    static RegisteredStudentResponse from(Student student) {
        return new RegisteredStudentResponse(student.id(), student.username(), student.fullName(),
                student.studentCode());
    }
}