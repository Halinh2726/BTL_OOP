# Frontend — Hệ thống thanh toán phí gửi xe bằng điểm

React 18 + TypeScript + Vite · React Router · TanStack Query · react-hook-form + zod · Tailwind CSS.

## Chạy

Cần Node.js 20 trở lên.

```powershell
cd frontend
npm install
```

| Lệnh | Dùng khi |
|---|---|
| `npm run dev` | Chạy với **backend thật**. Backend phải đang chạy ở `http://localhost:8080` (xem README gốc). Mở http://localhost:5173 |
| `npm run dev:mock` | Chạy với **dữ liệu giả lập**, không cần backend hay MySQL. Góc trên có nhãn `MOCK` |
| `npm test` | Unit test (Vitest) |
| `npm run typecheck` / `npm run lint` | Kiểm tra kiểu / ESLint |
| `npm run build` | Build ra `dist/` (đã gồm typecheck) |

Backend chạy ở cổng khác: `$env:BACKEND_URL="http://localhost:8081"; npm run dev`

### Biến môi trường

| Biến | Giá trị | Ý nghĩa |
|---|---|---|
| `VITE_API_MODE` | `http` (mặc định) \| `mock` | Gọi backend thật hay dùng dữ liệu giả lập. `npm run dev:mock` đặt sẵn `mock` (file `.env.mock`) |
| `BACKEND_URL` | mặc định `http://localhost:8080` | Đích của Vite proxy cho `/api` (chỉ khi dev) |

### Tài khoản

| Vai trò | Tài khoản | Backend thật | Mock |
|---|---|---|---|
| Quản lý | `quanly01` / `Quanly@123` | có sẵn (seed) | có |
| Chủ tài khoản | `chutk01` / `Chutk@123` | có sẵn (seed) | có, 4.000 điểm |
| Bảo vệ | `baove01` / `Baove@123` | quản lý tạo ở tab **Bảo vệ** | có |
| Sinh viên | `sv01` / `MatKhau@123` | tự đăng ký ở `/register` | có, 44.000 điểm, xe `29A12345` |

Mock còn có `sv02` / `MatKhau@123` (2.000 điểm, xe `30B67890`) để thử trường hợp **không đủ điểm**. Dữ liệu mock nằm trong `sessionStorage` của tab — đóng tab là về dữ liệu ban đầu.

## Màn hình

| Đường dẫn | Vai trò | Nội dung |
|---|---|---|
| `/login`, `/register` | — | Đăng nhập; sinh viên tự đăng ký |
| `/student` | Sinh viên | Số điểm, bảng phí, xe của tôi + đăng ký xe, lịch sử giao dịch (lọc, "Xem thêm") |
| `/guard` | Bảo vệ | Tab **Xe vào** / **Xe ra**: nhập biển số → tra cứu → kết luận xanh/đỏ → xác nhận → "Xe tiếp theo". Chữ và nút cỡ lớn cho máy tính bảng ở cổng |
| `/manager` | Quản lý | Tab **Đổi điểm** (tìm MSSV → nhập tiền → xác nhận → biên nhận), **Rút điểm** (ví chủ tài khoản), **Bảo vệ** (tạo, khóa/mở khóa) |
| `/owner` | Chủ tài khoản | Số điểm nhận từ phí gửi xe, lịch sử |

Sau khi đăng nhập, mỗi vai trò được đưa về trang của mình; mở trang của vai trò khác thì thấy "Không có quyền". Đây chỉ là trải nghiệm — **backend mới là nơi kiểm tra quyền**.

## Cấu trúc

```
src/
  app/                 App (providers, routes), AppLayout, RequireRole
  features/
    auth/              session (GET /me khôi phục phiên), LoginPage, RegisterPage
    student/           StudentPage, FeeTable, VehiclesCard, RegisterVehicleDialog
    guard/             GuardPage, CheckInPanel, CheckOutPanel, PlateLookupForm, ...
    manager/           ManagerPage, DepositTab, WithdrawTab, GuardsTab, CreateGuardDialog
    owner/             OwnerPage
  shared/
    api/               ApiClient (interface), httpApiClient, mock/mockApiClient, ApiError, queryKeys
    components/        Button, Field, Card, Dialog, Tabs, BalanceCard, TransactionHistory, ...
    hooks/             useIdempotencyKey, useFieldErrors, useVehicleTypeName
    types/api.ts       Kiểu dữ liệu của 23 API — khớp đúng JSON của backend
    utils/format.ts    Định dạng số vi-VN, ngày giờ, nhãn tiếng Việt
```

`ApiClient` là một **interface** với hai cài đặt thay thế được cho nhau: `httpApiClient` (gọi backend) và `mockApiClient` (giả lập). Màn hình chỉ phụ thuộc interface nên không biết đang dùng cái nào.

## Quy ước gọi API (theo Đặc tả API)

- **Cùng origin, không CORS:** client gọi đường dẫn tương đối `/api/v1/...`. Khi dev, Vite proxy chuyển sang backend; khi triển khai, Nginx làm việc này.
- **Phiên:** cookie `SESSION` (HttpOnly) do backend quản lý. Không dùng JWT, không lưu gì vào `localStorage`. Tải lại trang → `GET /me` để khôi phục.
- **CSRF:** mọi POST/PATCH (trừ đăng nhập, đăng ký) gửi header `X-XSRF-TOKEN` lấy từ cookie `XSRF-TOKEN`. Nếu backend trả `CSRF_INVALID` (token đổi sau khi đăng nhập), client lấy token mới và gửi lại **một lần**.
- **Idempotency-Key:** đổi điểm, rút điểm, xe ra. Key sinh khi **mở bước xác nhận**, giữ nguyên khi bấm lại hoặc thử lại sau lỗi mạng, nên không bao giờ cộng/trừ điểm hai lần. Response có `Idempotent-Replayed: true` vẫn là thành công (biên nhận ghi chú "đã ghi nhận trước đó").
- **Lỗi:** xử lý theo `code`, hiện `message` của backend. `VALIDATION_ERROR` hiện `fields[].message` dưới đúng ô nhập. `401`/`ACCOUNT_LOCKED` khi đang dùng → về trang đăng nhập kèm lời nhắn. `429` → kèm thời gian chờ từ `Retry-After`.
- **Không tự tính nghiệp vụ:** phí, số dư, số điểm cộng/trừ đều hiển thị theo response. Số "sẽ cộng khoảng X điểm" khi nhập tiền chỉ là ước tính, có ghi rõ.
