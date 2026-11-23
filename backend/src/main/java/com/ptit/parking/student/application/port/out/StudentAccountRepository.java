package com.ptit.parking.student.application.port.out;

import com.ptit.parking.auth.domain.Student;

/** Port: kiểm tra trùng và lưu tài khoản sinh viên mới (bảng users + students). */
public interface StudentAccountRepository {

    boolean studentCodeExists(String studentCode);

    boolean usernameExists(String username);

    default boolean emailExists(String email) {
        return false;
    }

    /** Lưu sinh viên CHƯA có id; trả về sinh viên đã có id do CSDL sinh. */
    Student save(Student student);

    /** Lưu sinh viên mới kèm email khôi phục tùy chọn (nullable). */
    default Student save(Student student, String email) {
        return save(student);
    }
}