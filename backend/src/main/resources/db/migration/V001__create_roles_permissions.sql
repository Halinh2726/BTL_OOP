-- =====================================================================
-- V001: Vai trò và quyền (RBAC)
-- Quyền là DỮ LIỆU, không hóa cứng trong code: đổi quyền một vai trò
-- chỉ cần sửa bảng role_permissions, không phải sửa/biên dịch lại Java.
-- =====================================================================

CREATE TABLE roles (
    code        VARCHAR(30)  NOT NULL PRIMARY KEY,   -- SINH_VIEN, BAO_VE, QUAN_LY, CHU_TAI_KHOAN
    name        VARCHAR(100) NOT NULL
);

CREATE TABLE permissions (
    code        VARCHAR(50)  NOT NULL PRIMARY KEY,   -- VEHICLE_REGISTER, PARKING_CHECK_IN, ...
    description VARCHAR(200) NOT NULL
);

CREATE TABLE role_permissions (
    role_code       VARCHAR(30) NOT NULL,
    permission_code VARCHAR(50) NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_code, permission_code),
    CONSTRAINT fk_role_permissions_role       FOREIGN KEY (role_code)       REFERENCES roles (code),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_code) REFERENCES permissions (code)
);
