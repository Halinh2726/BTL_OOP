package com.ptit.parking.student.application.port.out;

import com.ptit.parking.auth.domain.Student;

/** Port: kiểm tra trùng và lưu tài khoản sinh viên mới (bảng users + students). */
public interface StudentAccountRepository {

    boolean studentCodeExists(String studentCode);

    boolean usernameExists(String username);

    /** Lưu sinh viên CHƯA có id; trả về sinh viên đã có id do CSDL sinh. */
    Student save(Student student);
}