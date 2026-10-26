-- =====================================================================
-- V002: Người dùng — ánh xạ phân cấp kế thừa User (abstract)
--        ├── Student  (students)
--        ├── Guard    (guards)
--        ├── Manager  (chỉ cần dòng users, không có trường riêng)
--        └── Owner    (chỉ cần dòng users, không có trường riêng)
-- Kiểu ánh xạ: "Class Table Inheritance" — trường chung ở users,
-- trường riêng của từng lớp con nằm ở bảng con, khóa chính = khóa ngoại.
-- Thời gian lưu theo UTC (server MySQL chạy với default-time-zone = +00:00).
-- =====================================================================

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,                     -- BCrypt, không bao giờ lưu mật khẩu rõ
    full_name     VARCHAR(100) NOT NULL,
    role_code     VARCHAR(30)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'HOAT_DONG',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_users_username  UNIQUE (username),
    CONSTRAINT uq_users_id_role   UNIQUE (id, role_code),    -- để bảng con tham chiếu kèm vai trò
    CONSTRAINT fk_users_role      FOREIGN KEY (role_code) REFERENCES roles (code),
    CONSTRAINT ck_users_status    CHECK (status IN ('HOAT_DONG', 'BI_KHOA')),
    -- 'c' = so khớp PHÂN BIỆT hoa thường (collation mặc định của MySQL không phân biệt)
    CONSTRAINT ck_users_username  CHECK (REGEXP_LIKE(username, '^[a-z0-9._]{4,50}$', 'c')),
    CONSTRAINT ck_users_full_name CHECK (CHAR_LENGTH(TRIM(full_name)) BETWEEN 1 AND 100)
);

-- Sinh viên: role_code cố định 'SINH_VIEN' + khóa ngoại kép (user_id, role_code)
-- => CSDL tự đảm bảo một bảo vệ/quản lý KHÔNG THỂ có dòng trong students.
CREATE TABLE students (
    user_id      BIGINT      NOT NULL PRIMARY KEY,
    student_code VARCHAR(20) NOT NULL,
    role_code    VARCHAR(30) NOT NULL DEFAULT 'SINH_VIEN',

    CONSTRAINT uq_students_code UNIQUE (student_code),
    CONSTRAINT ck_students_role CHECK (role_code = 'SINH_VIEN'),
    CONSTRAINT ck_students_code CHECK (REGEXP_LIKE(student_code, '^[A-Z0-9]{1,20}$', 'c')),
    CONSTRAINT fk_students_user FOREIGN KEY (user_id, role_code) REFERENCES users (id, role_code)
);

-- Bảo vệ: R14 — chỉ khóa (users.status), không xóa, vì lượt gửi cũ còn tham chiếu.
CREATE TABLE guards (
    user_id   BIGINT      NOT NULL PRIMARY KEY,
    phone     VARCHAR(11) NOT NULL,
    role_code VARCHAR(30) NOT NULL DEFAULT 'BAO_VE',

    CONSTRAINT ck_guards_role  CHECK (role_code = 'BAO_VE'),
    CONSTRAINT ck_guards_phone CHECK (REGEXP_LIKE(phone, '^0[0-9]{9,10}$')),
    CONSTRAINT fk_guards_user  FOREIGN KEY (user_id, role_code) REFERENCES users (id, role_code)
);
