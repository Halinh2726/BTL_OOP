# Thiết kế CSDL — Hệ thống thanh toán phí gửi xe bằng điểm

MySQL 8.4 (LTS), quản lý bằng Flyway (`backend/src/main/resources/db/migration`).
Thời gian lưu theo UTC (`DATETIME(6)`), ứng dụng đổi sang giờ Việt Nam khi tính phí và hiển thị.

## 1. Sơ đồ ERD

```mermaid
erDiagram
    roles ||--o{ role_permissions : has
    permissions ||--o{ role_permissions : granted
    roles ||--o{ users : "role_code"

    users ||--o| students : "là (SINH_VIEN)"
    users ||--o| guards   : "là (BAO_VE)"
    users ||--o| wallets  : "sở hữu"

    students ||--o{ vehicles : "đăng ký"
    vehicle_types ||--o{ vehicles : "loại"

    vehicles ||--o{ parking_sessions : "lượt gửi"
    guards ||--o{ parking_sessions : "xác nhận vào/ra"

    wallets ||--o{ point_transactions : "nguồn / đích"
    users   ||--o{ point_transactions : "performed_by"
    point_transactions ||--o| cash_details : "DOI_DIEM / RUT_DIEM"
    point_transactions ||--o| parking_payment_details : "THANH_TOAN"
    parking_sessions ||--o| parking_payment_details : "thanh toán 1 lần"

    wallets ||--|| system_config : "receiving_wallet_id"

    users {
        bigint id PK
        varchar username UK
        varchar password_hash
        varchar full_name
        varchar role_code FK
        varchar status "HOAT_DONG | BI_KHOA"
    }
    students { bigint user_id PK "FK users" varchar student_code UK }
    guards   { bigint user_id PK "FK users" varchar phone }
    wallets  { bigint id PK bigint user_id UK bigint balance "CHECK >= 0" }
    vehicles { bigint id PK bigint student_id FK varchar plate_number UK varchar vehicle_type_code FK }
    parking_sessions {
        bigint id PK
        bigint vehicle_id FK
        varchar status "DANG_GUI | DA_RA"
        timestamptz entered_at
        bigint entry_guard_id FK
        timestamptz exited_at
        bigint exit_guard_id FK
    }
    point_transactions {
        bigint id PK
        varchar type "DOI_DIEM | THANH_TOAN | RUT_DIEM"
        bigint points "> 0"
        bigint source_wallet_id FK
        bigint target_wallet_id FK
        bigint source_balance_after
        bigint target_balance_after
        uuid idempotency_key UK
        bigint performed_by FK
    }
    cash_details { bigint transaction_id PK bigint cash_amount int cash_per_point }
    parking_payment_details { bigint transaction_id PK bigint session_id UK }
    fee_rules { bigint id PK time start_time UK int fee_points }
    system_config { smallint id PK "= 1" int cash_per_point bigint receiving_wallet_id FK }
```

## 2. Bảng ↔ lớp Java ↔ khái niệm OOP

| Bảng | Lớp domain | OOP thể hiện |
|---|---|---|
| `users` + `students` / `guards` | `User` (abstract) → `Student`, `Guard`, `Manager`, `Owner` | **Kế thừa**, **Trừu tượng hóa** (Class Table Inheritance) |
| `wallets` | `Wallet` | **Đóng gói**: `balance` chỉ đổi qua `credit()` / `debit()` |
| `point_transactions` + `cash_details` / `parking_payment_details` | `PointTransaction` (abstract sealed) → `CashDeposit`, `CashWithdrawal`, `ParkingPayment` | **Đa hình**: `changeFor(walletId)` |
| `fee_rules` | `FeePolicy` (interface) ← `TimeSlotFeePolicy` | **Trừu tượng hóa**, Strategy |
| `parking_sessions` | `ParkingSession` | **Đóng gói**: `close()` ghi giờ ra + bảo vệ cùng lúc |
| `roles`, `permissions`, `role_permissions` | (dữ liệu RBAC) | Quyền là dữ liệu, không hóa cứng |

## 3. Quy tắc nghiệp vụ ↔ ràng buộc CSDL

| Quy tắc | Ràng buộc |
|---|---|
| R01 điểm không âm | `ck_wallets_balance`, `ck_point_transactions_balances` |
| R05 mỗi xe tối đa 1 lượt mở | `uq_parking_sessions_open` trên cột sinh tự động `open_vehicle_id` (= `vehicle_id` khi `DANG_GUI`, `NULL` khi `DA_RA`) — cách làm "unique có điều kiện" trong MySQL |
| Một lượt gửi chỉ thanh toán 1 lần | `uq_payment_details_session` |
| Bấm xác nhận nhiều lần chỉ 1 giao dịch | `uq_point_transactions_idempotency` |
| R06/R09 nguồn/đích đúng theo loại giao dịch | `ck_point_transactions_shape` |
| R10 giao dịch không sửa/xóa | trigger `trg_*_no_update`, `trg_*_no_delete` (`SIGNAL SQLSTATE '45000'`) |
| R11 lưu tỷ lệ tại thời điểm giao dịch | `cash_details.cash_per_point` |
| R12, R13 đối soát | view `v_reconciliation` |
| R14 bảo vệ chỉ khóa, không xóa | `users.status` + FK từ `parking_sessions` (xóa sẽ bị chặn) |
| UNIQUE MSSV / username / biển số | `uq_students_code`, `uq_users_username`, `uq_vehicles_plate` |
| Bảo vệ không thể có MSSV | FK kép `(user_id, role_code)` + `ck_students_role` |

Tên ràng buộc được backend ánh xạ sang mã lỗi API (ví dụ `uq_vehicles_plate` → `PLATE_EXISTS`).

## 4. Tài khoản seed (chỉ để demo)

| Vai trò | Username | Mật khẩu |
|---|---|---|
| Quản lý | `quanly01` | `Quanly@123` |
| Chủ tài khoản | `chutk01` | `Chutk@123` |
