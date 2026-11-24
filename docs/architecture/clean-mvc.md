# Clean Architecture kết hợp MVC

## 1. Quyết định kiến trúc

Backend là một **modular monolith theo nghiệp vụ**. Mỗi module giữ bốn vòng chính: `domain`, `application`, `presentation` và `infrastructure`; `configuration` là composition root dùng để lắp ráp các dependency.

MVC không thay thế Clean Architecture. MVC được giới hạn trong tầng `presentation`:

- **Model (presentation model):** request DTO, response DTO và view model trả về JSON. Đây không phải domain entity.
- **View:** giao diện React ở frontend; đối với REST API, JSON response là dữ liệu dành cho View.
- **Controller:** Spring REST controller nhận HTTP request, kiểm tra dữ liệu, gọi use case và ánh xạ kết quả.

Luồng phụ thuộc chuẩn:

```text
React View
    |
    v
presentation (MVC Controller + presentation model)
    |
    v
application (use case + ports)
    |
    v
domain (entity, value object, domain service)

infrastructure (JDBC/JPA, security, email, VietQR)
    |
    +---- implements application port/out

configuration ---- wires all layers together
```

## 2. Cấu trúc backend

```text
backend/src/main/java/com/ptit/parking/
├── ParkingApplication.java
├── account/
├── auth/
├── catalog/
├── gate/
├── manager/
├── notification/
├── owner/
├── parking/
├── pass/
├── payment/
├── report/
├── student/
├── support/
└── shared/
```

Mỗi module nghiệp vụ dùng cấu trúc sau; chỉ tạo thư mục khi module thực sự cần:

```text
<module>/
├── domain/
│   ├── model/                 # Có thể để phẳng khi module còn nhỏ
│   ├── service/               # Domain service thuần Java
│   └── event/                 # Domain event
├── application/
│   ├── usecase/               # Application service / use case
│   ├── port/in/               # Hợp đồng use case khi cần nhiều inbound adapter
│   ├── port/out/              # Repository và external-service contract
│   └── event/                 # Application event
├── presentation/
│   ├── web/                   # Controller, request/response, web mapper
│   └── startup/               # Inbound adapter chạy lúc startup
├── infrastructure/
│   ├── persistence/           # JDBC/JPA entity, repository và adapter
│   ├── security/              # BCrypt, secret generator...
│   ├── notification/          # SMTP, logging mail...
│   ├── event/                 # Spring event publisher
│   └── vietqr/                # Cổng tích hợp VietQR
└── configuration/             # @Configuration và bean wiring
```

`shared` cũng tuân theo các vòng tương tự:

```text
shared/
├── domain/
├── application/
├── presentation/web/
├── infrastructure/
│   ├── event/
│   ├── persistence/
│   ├── ratelimit/
│   └── security/
└── configuration/
```

## 3. Dependency Rule

| Tầng | Được phụ thuộc | Không được phụ thuộc |
|---|---|---|
| `domain` | JDK, `shared.domain` tối thiểu | Spring, Jakarta Persistence, controller, JDBC/JPA adapter |
| `application` | `domain`, application contract của module khác khi thật sự cần | `presentation`, `infrastructure`, Spring MVC, JPA entity |
| `presentation` | `application`, presentation model, Spring MVC/Validation/Security | JDBC/JPA repository hoặc infrastructure adapter |
| `infrastructure` | `application.port.out`, `domain`, framework triển khai | `presentation` |
| `configuration` | Tất cả tầng để lắp ráp | Chứa nghiệp vụ hoặc câu lệnh SQL |

Quy tắc giữa các module:

1. Không truy cập trực tiếp persistence adapter của module khác.
2. Giao tiếp qua use case/application port hoặc application event.
3. Không đưa class vào `shared` chỉ vì nó được dùng hai lần; chỉ đưa vào khi nó thực sự không thuộc một bounded context cụ thể.
4. Controller không chứa quy tắc nghiệp vụ, transaction hoặc SQL.
5. Domain entity không được dùng trực tiếp làm request/response API.

## 4. Cấu trúc frontend

Frontend là View của hệ thống MVC và được tổ chức theo hướng feature-first:

```text
frontend/src/
├── app/                       # Router, provider, layout, guard
├── pages/                     # Route-level composition (mục tiêu tiếp theo)
├── features/                  # Luồng thao tác của người dùng
├── entities/                  # UI/model dùng lại theo thực thể (khi phát sinh)
├── widgets/                   # Khối giao diện ghép nhiều feature (khi phát sinh)
└── shared/
    ├── api/
    ├── components/
    ├── hooks/
    ├── types/
    └── utils/
```

Không đưa domain rule quan trọng chỉ vào frontend. Frontend thực hiện validation phục vụ trải nghiệm; backend vẫn là nguồn xác thực nghiệp vụ cuối cùng.

## 5. Ánh xạ từ cấu trúc cũ

| Cấu trúc cũ | Cấu trúc mới |
|---|---|
| `adapter/in/web` | `presentation/web` |
| `adapter/in/startup` | `presentation/startup` |
| `adapter/out/persistence` | `infrastructure/persistence` |
| `adapter/out/security` | `infrastructure/security` |
| `adapter/out/notification` | `infrastructure/notification` |
| `adapter/out/event` | `infrastructure/event` |
| `config` | `configuration` |
| `shared.web` | `shared.presentation.web` |
| `shared.security` | `shared.infrastructure.security` |
| `shared.ratelimit` | `shared.infrastructure.ratelimit` |

Test package phải phản chiếu package của production code. Integration test xuyên module tiếp tục đặt tại `com.ptit.parking.integration`.

## 6. Quy tắc refactor và Git

- Dùng `git diff --find-renames` để review các lần di chuyển file.
- Một commit chỉ nên chứa một nhóm đổi package hoặc một thay đổi nghiệp vụ, không trộn cả hai.
- Chạy `mvn clean test` sau khi đổi package để không giữ bytecode của package cũ trong `target`.
- Chạy `npm test` và `npm run build` trước khi mở pull request.
- Trước khi push cuối, cập nhật `origin/develop` và giải quyết conflict trên nhánh refactor, không xử lý trực tiếp trên `develop`.
