# Hệ thống thanh toán phí gửi xe bằng điểm

Bài tập lớn môn Lập trình hướng đối tượng. Sinh viên đổi tiền mặt lấy điểm, mỗi lần gửi xe hệ thống
**tự trừ điểm** khi bảo vệ xác nhận xe ra và cộng cho chủ tài khoản bãi xe; chủ tài khoản rút điểm ra tiền mặt qua quản lý.

| Vai trò | Làm được gì |
|---|---|
| Sinh viên | Tự đăng ký tài khoản, đăng ký xe, xem số điểm và lịch sử giao dịch |
| Bảo vệ | Tra cứu biển số, xác nhận xe vào, xác nhận xe ra (hệ thống tự trừ phí) |
| Quản lý | Đổi tiền mặt sang điểm cho sinh viên, rút điểm cho chủ tài khoản, tạo / khóa / mở khóa bảo vệ |
| Chủ tài khoản | Xem số điểm đã nhận và lịch sử nhận phí, rút điểm |

## Công nghệ

- Java 21, Spring Boot 4, Spring Security (phiên phía máy chủ, CSRF), Maven Wrapper
- MySQL 8.4 chạy bằng Docker, Flyway quản lý schema (`backend/src/main/resources/db/migration`)
- JUnit 5, Mockito, AssertJ (294 test), Postman / Newman cho kịch bản demo
- Giao diện: React 18 + TypeScript + Vite, TanStack Query, Tailwind CSS, Vitest (`frontend/`)

## Chạy dự án

**Cần cài:** JDK 21 trở lên, Docker Desktop. Không cần cài MySQL hay Maven.

```powershell
# 1. Bật MySQL (lần đầu sẽ tải image mysql:8.4)
docker compose up -d

# 2. Chạy backend — Flyway tự tạo bảng và dữ liệu mẫu ở lần chạy đầu
cd backend
.\mvnw.cmd spring-boot:run          # macOS / Linux: ./mvnw spring-boot:run
```

Chờ dòng `Started ParkingApplication`, rồi kiểm tra:

```powershell
curl.exe http://localhost:8080/api/v1/health
# {"status":"UP","database":"UP"}
```

Mặc định dùng CSDL `parking` (user `parking` / `parking`) trên cổng 3306, backend cổng 8080 — không cần cấu hình gì.
Muốn đổi thì sao chép `.env.example` thành `.env` và sửa (cho `docker compose`), đồng thời đặt các biến môi trường
cùng tên (`DB_PORT`, `DB_PASSWORD`, `SERVER_PORT`...) trước khi chạy backend.

**Giao diện web** (cần Node.js 20+), mở terminal thứ hai khi backend đang chạy:

```powershell
cd frontend
npm install
npm run dev                         # mở http://localhost:5173
```

Chỉ muốn xem giao diện, không cần backend: `npm run dev:mock` (dữ liệu giả lập). Chi tiết: [`frontend/README.md`](frontend/README.md).

**Xóa sạch dữ liệu để demo lại từ đầu:** `docker compose down -v` rồi `docker compose up -d`
(giao dịch điểm không sửa / xóa được bằng API theo R10, nên làm lại từ đầu là xóa cả CSDL).

## Tài khoản có sẵn

| Vai trò | Tên đăng nhập | Mật khẩu |
|---|---|---|
| Quản lý | `quanly01` | `Quanly@123` |
| Chủ tài khoản | `chutk01` | `Chutk@123` |

Sinh viên tự đăng ký qua `POST /students/register`; bảo vệ do quản lý tạo qua `POST /manager/guards`.
Dữ liệu danh mục có sẵn: loại xe `XE_MAY`, `XE_DAP`, `XE_DAP_DIEN`; phí gửi **3.000 điểm** nếu xe ra trước 18:00,
**5.000 điểm** từ 18:00 (giờ Việt Nam); tỷ lệ **1 đồng = 1 điểm**.

## Kịch bản demo bằng Postman

File: [`docs/postman/parking-payment.postman_collection.json`](docs/postman/parking-payment.postman_collection.json)
— 36 request, 68 kiểm tra tự động, chạy hết luồng nghiệp vụ theo thứ tự:

| Bước | Ai | Việc |
|---|---|---|
| 0 | — | Kiểm tra hệ thống (`/health`) |
| 1 | Sinh viên | Đăng ký tài khoản, đăng nhập, đăng ký xe, xem ví (0 điểm) |
| 2 | Quản lý | Tra cứu MSSV, đổi 50.000đ → 50.000 điểm, bấm lại cùng key (không cộng 2 lần), tạo bảo vệ |
| 3 | Bảo vệ | Xem trước xe vào, cho vào, cho vào lần nữa (bị chặn), xem trước xe ra, cho ra → tự trừ phí, bấm lại (không trừ 2 lần) |
| 4 | Sinh viên | Ví còn 50.000 − phí, lịch sử có THANH_TOAN và DOI_DIEM, gọi API quản lý → 403 |
| 5 | Chủ tài khoản | Ví và lịch sử nhận phí |
| 6 | Quản lý | Rút điểm cho chủ tài khoản, khóa bảo vệ (bảo vệ đăng nhập → 403), mở khóa, đăng xuất |

**Cách chạy trong Postman:** Import file trên → chuột phải vào collection → **Run collection** → **Run**.
Mỗi lần chạy tự sinh MSSV, tên đăng nhập, biển số mới, nên chạy lại bao nhiêu lần cũng được.
Token CSRF được script của collection tự lấy từ cookie `XSRF-TOKEN`; không cần nhập tay.

**Chạy bằng dòng lệnh** (cần Node.js):

```powershell
npx newman run docs/postman/parking-payment.postman_collection.json
```

Collection **ghi dữ liệu thật** (tạo sinh viên, bảo vệ, giao dịch) vào CSDL đang chạy — chỉ chạy trên CSDL demo.

## Danh sách API

Địa chỉ gốc: `http://localhost:8080/api/v1`. Chi tiết request / response / mã lỗi: tài liệu Đặc tả API.

| # | Method | Đường dẫn | Quyền | Chức năng |
|---|---|---|---|---|
| 1 | POST | `/auth/login` | Công khai | Đăng nhập |
| 2 | POST | `/auth/logout` | Đăng nhập | Đăng xuất |
| 3 | GET | `/me` | Đăng nhập | Thông tin phiên hiện tại |
| 4 | POST | `/students/register` | Công khai | Sinh viên đăng ký tài khoản |
| 5 | GET | `/me/vehicles` | VEHICLE_REGISTER | Danh sách xe của tôi |
| 6 | POST | `/me/vehicles` | VEHICLE_REGISTER | Đăng ký xe |
| 7 | GET | `/me/wallet` | WALLET_VIEW_OWN | Số điểm hiện có (sinh viên, chủ tài khoản) |
| 8 | GET | `/me/transactions` | WALLET_VIEW_OWN | Lịch sử giao dịch (`?type=&limit=&before=`) |
| 9 | GET | `/vehicle-types` | Đăng nhập | Danh sách loại xe |
| 10 | GET | `/fee-rules` | Đăng nhập | Bảng giá gửi xe |
| 11 | GET | `/config/exchange-rate` | Đăng nhập | Tỷ lệ quy đổi hiện hành |
| 12 | GET | `/guard/check-in/preview?plate=` | PARKING_CHECK_IN | Tra cứu trước khi cho xe vào |
| 13 | POST | `/guard/check-in` | PARKING_CHECK_IN | Xác nhận cho xe vào |
| 14 | GET | `/guard/check-out/preview?plate=` | PARKING_CHECK_OUT | Tra cứu trước khi cho xe ra |
| 15 | POST | `/guard/check-out` | PARKING_CHECK_OUT | Xác nhận xe ra và tự thanh toán |
| 16 | GET | `/manager/students/{studentCode}` | CASH_DEPOSIT | Tra cứu sinh viên theo MSSV |
| 17 | POST | `/manager/deposits` | CASH_DEPOSIT | Đổi tiền mặt sang điểm |
| 18 | GET | `/manager/owner-wallet` | CASH_WITHDRAWAL | Ví của chủ tài khoản |
| 19 | POST | `/manager/withdrawals` | CASH_WITHDRAWAL | Rút điểm ra tiền mặt |
| 20 | GET | `/manager/guards` | GUARD_CREATE hoặc GUARD_LOCK | Danh sách bảo vệ |
| 21 | POST | `/manager/guards` | GUARD_CREATE | Tạo tài khoản bảo vệ |
| 22 | PATCH | `/manager/guards/{id}/status` | GUARD_LOCK | Khóa / mở khóa bảo vệ |
| 23 | GET | `/health` | Công khai | Kiểm tra tình trạng dịch vụ |
| 24 | GET | `/guard/plates?q=&parked=` | PARKING_CHECK_IN hoặc PARKING_CHECK_OUT | Gợi ý biển số khi bảo vệ gõ vài ký tự |

### Quy ước khi gọi API

- **Phiên đăng nhập:** cookie `SESSION` (HttpOnly) do máy chủ cấp khi đăng nhập; hết hạn sau 30 phút không hoạt động
  hoặc 12 giờ kể từ lúc đăng nhập. Không dùng JWT.
- **CSRF:** mọi request ghi dữ liệu (trừ đăng nhập, đăng ký) phải gửi header `X-XSRF-TOKEN` bằng giá trị cookie `XSRF-TOKEN`.
- **Idempotency-Key:** bắt buộc (một UUID) với `POST /manager/deposits`, `/manager/withdrawals`, `/guard/check-out`.
  Gửi lại cùng key → trả lại đúng giao dịch cũ kèm header `Idempotent-Replayed: true`, không cộng / trừ điểm lần hai.
- **Lỗi:** luôn có dạng `{"code","message","status","traceId","fields"}`; `fields` chỉ có với `VALIDATION_ERROR`.
- **Giới hạn tần suất:** đăng nhập sai 5 lần / phút cho mỗi IP + tên đăng nhập; đăng ký 10 lần / giờ / IP;
  mọi API khác 120 request / phút / phiên. Vượt → `429 TOO_MANY_REQUESTS` kèm header `Retry-After` (giây).
- **Thời gian:** lưu UTC trong CSDL, trả về theo giờ Việt Nam (`+07:00`).

## Kiến trúc

Modular Monolith: mỗi nghiệp vụ là một module trong `backend/src/main/java/com/ptit/parking/`, bên trong chia lớp
theo Clean Architecture (Ports & Adapters):

| Module | Nội dung |
|---|---|
| `auth` | Đăng nhập, phiên, các lớp người dùng `User` → `Student`, `Guard`, `Manager`, `Owner` |
| `student` | Đăng ký sinh viên, đăng ký xe, biển số `PlateNumber` |
| `catalog` | Loại xe, bảng giá, tỷ lệ quy đổi |
| `parking` | Lượt gửi `ParkingSession`, cách tính phí `FeePolicy` (Strategy), xe vào / xe ra |
| `payment` | Ví `Wallet`, giao dịch `PointTransaction` → `CashDeposit`, `ParkingPayment`, `CashWithdrawal`; chống trừ hai lần |
| `manager` | Đổi điểm, rút điểm, quản lý bảo vệ |
| `shared` | Bảo mật, xử lý lỗi, giới hạn tần suất, transaction |

```
<module>/
  domain/                  quy tắc nghiệp vụ, Java thuần, không phụ thuộc Spring
  application/usecase/     điều phối một nghiệp vụ (một transaction)
  application/port/out/    interface: use case cần gì từ bên ngoài (CSDL...)
  adapter/in/web/          controller REST, DTO request / response
  adapter/out/persistence/ cài đặt port bằng JDBC / JPA
  config/                  lắp ráp use case thành bean Spring
```

Thiết kế CSDL: [`docs/database/ERD.md`](docs/database/ERD.md).

## Chạy test

```powershell
cd backend
.\mvnw.cmd test
# Tests run: 294, Failures: 0, Errors: 0
```

Test không cần MySQL: use case được test với kho dữ liệu giả trong bộ nhớ, controller test bằng `@WebMvcTest`
với cấu hình bảo mật thật.

## Lỗi thường gặp

| Hiện tượng | Nguyên nhân và cách xử lý |
|---|---|
| Backend báo `Access denied for user 'parking'` | Có MySQL cài sẵn trên máy (dịch vụ `MySQL80`...) chiếm cổng 3306. Mở cmd quyền Administrator: `net stop MySQL80`, rồi `docker compose up -d` |
| `curl` trả `000` / không kết nối được | Backend chưa khởi động xong — chờ dòng `Started ParkingApplication` |
| `403 CSRF_INVALID` | Thiếu header `X-XSRF-TOKEN`, hoặc giá trị không khớp cookie `XSRF-TOKEN` |
| `429 TOO_MANY_REQUESTS` khi thử nhiều lần | Vượt giới hạn tần suất; chờ số giây trong `Retry-After`, hoặc khởi động lại backend (bộ đếm nằm trong bộ nhớ) |
| `400 IDEMPOTENCY_KEY_REQUIRED` | Đổi điểm / rút điểm / xe ra cần header `Idempotency-Key` là một UUID |

## Quy ước nhánh

- `main`: bản ổn định để nộp / demo
- `develop`: nhánh tích hợp
- `feature/BTL-<số>-<mã task>-<mô tả>`: mỗi task một nhánh, tách từ `develop`, merge lại qua Pull Request
