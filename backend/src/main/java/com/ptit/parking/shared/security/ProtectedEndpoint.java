package com.ptit.parking.shared.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpMethod;

import static com.ptit.parking.shared.security.Permission.ACCOUNT_MANAGE;
import static com.ptit.parking.shared.security.Permission.CASE_HANDLE;
import static com.ptit.parking.shared.security.Permission.COMPENSATION_APPROVE;
import static com.ptit.parking.shared.security.Permission.COMPLAINT_SUBMIT;
import static com.ptit.parking.shared.security.Permission.PASS_BUY;
import static com.ptit.parking.shared.security.Permission.SHIFT_PLAN;
import static com.ptit.parking.shared.security.Permission.CASH_DEPOSIT;
import static com.ptit.parking.shared.security.Permission.CASH_RECONCILE;
import static com.ptit.parking.shared.security.Permission.CASH_WITHDRAWAL;
import static com.ptit.parking.shared.security.Permission.GUARD_CREATE;
import static com.ptit.parking.shared.security.Permission.GUARD_LOCK;
import static com.ptit.parking.shared.security.Permission.INCIDENT_REPORT;
import static com.ptit.parking.shared.security.Permission.LEDGER_VIEW;
import static com.ptit.parking.shared.security.Permission.OWNER_FINANCE;
import static com.ptit.parking.shared.security.Permission.POINT_ADJUST;
import static com.ptit.parking.shared.security.Permission.PARKING_CHECK_IN;
import static com.ptit.parking.shared.security.Permission.PARKING_CHECK_OUT;
import static com.ptit.parking.shared.security.Permission.STUDENT_VIEW;
import static com.ptit.parking.shared.security.Permission.TRAFFIC_STATS;
import static com.ptit.parking.shared.security.Permission.VEHICLE_REGISTER;
import static com.ptit.parking.shared.security.Permission.WALLET_VIEW_OWN;

/**
 * Ma trận "API nào cần quyền gì" — chép đúng cột Quyền của bảng tổng quan Đặc tả API mục 1.
 * <p>Mỗi hằng enum là MỘT dòng của bảng, tự mang method, đường dẫn và các quyền được phép.
 * SecurityConfig đọc bảng này để chặn ở backend; test cũng đọc CHÍNH bảng này để kiểm tra từng dòng,
 * nên đặc tả, cấu hình và test không thể lệch nhau.
 */
public enum ProtectedEndpoint {

    MY_VEHICLES        (HttpMethod.GET,   "/api/v1/me/vehicles",              VEHICLE_REGISTER),   // #5
    REGISTER_VEHICLE   (HttpMethod.POST,  "/api/v1/me/vehicles",              VEHICLE_REGISTER),   // #6
    UPDATE_VEHICLE     (HttpMethod.PATCH, "/api/v1/me/vehicles/*",            VEHICLE_REGISTER),   // Đợt 1: sửa loại xe
    DEACTIVATE_VEHICLE (HttpMethod.POST,  "/api/v1/me/vehicles/*/deactivate", VEHICLE_REGISTER),   // Đợt 1: ngừng sử dụng xe
    MY_WALLET          (HttpMethod.GET,   "/api/v1/me/wallet",                WALLET_VIEW_OWN),    // #7
    MY_DEPOSIT_INFO    (HttpMethod.GET,   "/api/v1/me/deposit-info",          WALLET_VIEW_OWN),    // Nạp điểm VietQR
    MY_TRANSACTIONS    (HttpMethod.GET,   "/api/v1/me/transactions",          WALLET_VIEW_OWN),    // #8
    CHECK_IN_PREVIEW   (HttpMethod.GET,   "/api/v1/guard/check-in/preview",   PARKING_CHECK_IN),   // #12
    CHECK_IN           (HttpMethod.POST,  "/api/v1/guard/check-in",           PARKING_CHECK_IN),   // #13
    CHECK_OUT_PREVIEW  (HttpMethod.GET,   "/api/v1/guard/check-out/preview",  PARKING_CHECK_OUT),  // #14
    CHECK_OUT          (HttpMethod.POST,  "/api/v1/guard/check-out",          PARKING_CHECK_OUT),  // #15
    CHECK_OUT_VIETQR   (HttpMethod.POST,  "/api/v1/guard/check-out/vietqr",   PARKING_CHECK_OUT),  // BV02-VietQR
    CHECK_OUT_CASH     (HttpMethod.POST,  "/api/v1/guard/check-out/cash",     PARKING_CHECK_OUT),  // BV02-Tiền mặt
    GUARD_CASH_SHEET   (HttpMethod.GET,   "/api/v1/guard/cash-sheet",         PARKING_CHECK_IN, PARKING_CHECK_OUT), // Bảng kê tiền mặt ca trực
    SUGGEST_PLATES     (HttpMethod.GET,   "/api/v1/guard/plates",             PARKING_CHECK_IN, PARKING_CHECK_OUT), // #24
    PARKED_VEHICLES    (HttpMethod.GET,   "/api/v1/guard/parked-vehicles",    PARKING_CHECK_IN, PARKING_CHECK_OUT), // Đợt 2
    MY_GATE_ACTIVITY   (HttpMethod.GET,   "/api/v1/guard/my-activity",        PARKING_CHECK_IN, PARKING_CHECK_OUT), // Đợt 2
    CURRENT_SHIFT      (HttpMethod.GET,   "/api/v1/guard/shifts/current",     PARKING_CHECK_IN, PARKING_CHECK_OUT), // Đợt 2
    START_SHIFT        (HttpMethod.POST,  "/api/v1/guard/shifts/start",       PARKING_CHECK_IN, PARKING_CHECK_OUT), // Đợt 2
    END_SHIFT          (HttpMethod.POST,  "/api/v1/guard/shifts/end",         PARKING_CHECK_IN, PARKING_CHECK_OUT), // Đợt 2
    MY_SHIFT_HISTORY   (HttpMethod.GET,   "/api/v1/guard/shifts/history",     PARKING_CHECK_IN, PARKING_CHECK_OUT), // Đợt 2: chấm công
    GUARD_TIMESHEET    (HttpMethod.GET,   "/api/v1/manager/guard-shifts",     GUARD_CREATE, GUARD_LOCK), // Đợt 2: chấm công
    // Đợt 3 — chủ bãi xe: tổng quan, doanh thu, yêu cầu rút điểm (tạo / hủy / xác nhận / khiếu nại)
    OWNER_OVERVIEW     (HttpMethod.GET,   "/api/v1/owner/overview",           OWNER_FINANCE),
    OWNER_REVENUE      (HttpMethod.GET,   "/api/v1/owner/revenue",            OWNER_FINANCE),
    OWNER_WITHDRAWALS  (HttpMethod.GET,   "/api/v1/owner/withdrawal-requests", OWNER_FINANCE),
    CREATE_WITHDRAWAL  (HttpMethod.POST,  "/api/v1/owner/withdrawal-requests", OWNER_FINANCE),
    OWNER_WITHDRAWAL_ACTION(HttpMethod.POST, "/api/v1/owner/withdrawal-requests/*/*", OWNER_FINANCE),
    // Đợt 3 — quản lý: xử lý yêu cầu rút (thay cho rút điểm tự do #19 đã bỏ), giao dịch điều chỉnh
    MANAGER_WITHDRAWALS(HttpMethod.GET,   "/api/v1/manager/withdrawal-requests", CASH_WITHDRAWAL),
    MANAGER_WITHDRAWAL_ACTION(HttpMethod.POST, "/api/v1/manager/withdrawal-requests/*/*", CASH_WITHDRAWAL),
    ADJUST_POINTS      (HttpMethod.POST,  "/api/v1/manager/adjustments",      POINT_ADJUST),
    // Đợt 4a — giám sát & đối soát
    SEARCH_STUDENTS    (HttpMethod.GET,   "/api/v1/manager/student-profiles",   STUDENT_VIEW),
    STUDENT_PROFILE    (HttpMethod.GET,   "/api/v1/manager/student-profiles/*", STUDENT_VIEW),
    LEDGER             (HttpMethod.GET,   "/api/v1/manager/ledger",             LEDGER_VIEW),
    LEDGER_EXPORT      (HttpMethod.GET,   "/api/v1/manager/ledger/export",      LEDGER_VIEW),
    RECONCILIATIONS    (HttpMethod.GET,   "/api/v1/manager/reconciliations",    CASH_RECONCILE),
    RECONCILIATION_DAY (HttpMethod.GET,   "/api/v1/manager/reconciliations/*",  CASH_RECONCILE),
    CLOSE_RECONCILIATION(HttpMethod.POST, "/api/v1/manager/reconciliations",    CASH_RECONCILE),
    TRAFFIC_STATS_VIEW (HttpMethod.GET,   "/api/v1/stats/traffic",              TRAFFIC_STATS),
    // Đợt 4b — khiếu nại (sinh viên), xử lý sự cố & khiếu nại (quản lý), bồi thường (chủ bãi xe), phân ca
    MY_COMPLAINTS      (HttpMethod.GET,   "/api/v1/me/complaints",              COMPLAINT_SUBMIT),
    SUBMIT_COMPLAINT   (HttpMethod.POST,  "/api/v1/me/complaints",              COMPLAINT_SUBMIT),
    CANCEL_COMPLAINT   (HttpMethod.POST,  "/api/v1/me/complaints/*/cancel",     COMPLAINT_SUBMIT),
    MY_COMPLAINT_PHOTO (HttpMethod.GET,   "/api/v1/me/complaints/*/photo",      COMPLAINT_SUBMIT),
    COMPLAINT_SESSIONS (HttpMethod.GET,   "/api/v1/me/complaint-sessions",      COMPLAINT_SUBMIT),
    MANAGER_COMPLAINTS (HttpMethod.GET,   "/api/v1/manager/complaints",         CASE_HANDLE),
    MANAGER_COMPLAINT_ACTION(HttpMethod.POST, "/api/v1/manager/complaints/*/*", CASE_HANDLE),
    MANAGER_COMPLAINT_PHOTO(HttpMethod.GET, "/api/v1/manager/complaints/*/photo", CASE_HANDLE),
    MANAGER_INCIDENTS  (HttpMethod.GET,   "/api/v1/manager/incidents",          CASE_HANDLE),
    MANAGER_INCIDENT_ACTION(HttpMethod.POST, "/api/v1/manager/incidents/*/*",   CASE_HANDLE),
    MANAGER_INCIDENT_PHOTO(HttpMethod.GET, "/api/v1/manager/incidents/*/photo", CASE_HANDLE),
    OWNER_COMPENSATIONS(HttpMethod.GET,   "/api/v1/owner/compensations",        COMPENSATION_APPROVE),
    OWNER_COMPENSATION_ACTION(HttpMethod.POST, "/api/v1/owner/compensations/*/*", COMPENSATION_APPROVE),
    SHIFT_PLAN_VIEW    (HttpMethod.GET,   "/api/v1/manager/shift-plan",         SHIFT_PLAN),
    SHIFT_ASSIGN       (HttpMethod.POST,  "/api/v1/manager/shift-plan",         SHIFT_PLAN),
    SHIFT_UNASSIGN     (HttpMethod.DELETE, "/api/v1/manager/shift-plan/*",      SHIFT_PLAN),
    MY_SCHEDULE        (HttpMethod.GET,   "/api/v1/guard/my-schedule",          PARKING_CHECK_IN, PARKING_CHECK_OUT),
    // Đợt 5 — vé tháng (thông báo /me/notifications chỉ cần đăng nhập: mọi vai trò đều có hộp thư)
    MY_PASSES          (HttpMethod.GET,   "/api/v1/me/passes",                  PASS_BUY),
    BUY_PASS           (HttpMethod.POST,  "/api/v1/me/passes",                  PASS_BUY),
    REPORT_INCIDENT    (HttpMethod.POST,  "/api/v1/guard/incidents",          INCIDENT_REPORT),    // Đợt 2
    MY_INCIDENTS       (HttpMethod.GET,   "/api/v1/guard/incidents",          INCIDENT_REPORT),    // Đợt 2
    INCIDENT_PHOTO     (HttpMethod.GET,   "/api/v1/guard/incidents/*/photo",  INCIDENT_REPORT),    // Đợt 2
    LOOKUP_STUDENT     (HttpMethod.GET,   "/api/v1/manager/students/*",       CASH_DEPOSIT),       // #16
    DEPOSIT            (HttpMethod.POST,  "/api/v1/manager/deposits",         CASH_DEPOSIT),       // #17
    OWNER_WALLET       (HttpMethod.GET,   "/api/v1/manager/owner-wallet",     CASH_WITHDRAWAL),    // #18
    LIST_GUARDS        (HttpMethod.GET,   "/api/v1/manager/guards",           GUARD_CREATE, GUARD_LOCK), // #20
    CREATE_GUARD       (HttpMethod.POST,  "/api/v1/manager/guards",           GUARD_CREATE),       // #21
    CHANGE_GUARD_STATUS(HttpMethod.PATCH, "/api/v1/manager/guards/*/status",  GUARD_LOCK),         // #22
    UPDATE_GUARD       (HttpMethod.PATCH, "/api/v1/manager/guards/*",         ACCOUNT_MANAGE),     // Đợt 1
    RESET_GUARD_PASSWORD  (HttpMethod.POST, "/api/v1/manager/guards/*/password-reset",   ACCOUNT_MANAGE), // Đợt 1
    RESET_STUDENT_PASSWORD(HttpMethod.POST, "/api/v1/manager/students/*/password-reset", ACCOUNT_MANAGE), // Đợt 1
    AUDIT_LOGS         (HttpMethod.GET,   "/api/v1/manager/audit-logs",       ACCOUNT_MANAGE),     // Đợt 1
    GET_BANK_CONFIG    (HttpMethod.GET,   "/api/v1/manager/bank-config",      CASH_DEPOSIT),       // Cấu hình VietQR
    UPDATE_BANK_CONFIG (HttpMethod.PUT,   "/api/v1/manager/bank-config",      CASH_DEPOSIT),       // Cập nhật VietQR
    RESET_BANK_CONFIG  (HttpMethod.DELETE,"/api/v1/manager/bank-config",      CASH_DEPOSIT),       // Khôi phục VietQR .env
    GET_PARKING_POLICY (HttpMethod.GET,   "/api/v1/manager/parking-policy",   CASH_DEPOSIT),       // Giờ khóa & phí qua đêm
    UPDATE_PARKING_POLICY(HttpMethod.PUT, "/api/v1/manager/parking-policy",   CASH_DEPOSIT),       // Cập nhật giờ khóa & phí qua đêm
    MANAGER_GATE_CASH  (HttpMethod.GET,   "/api/v1/manager/gate-cash",        CASH_RECONCILE);     // Tiền mặt bảo vệ thu theo ngày

    private final HttpMethod method;
    private final String pathPattern;
    private final List<Permission> anyOf;

    ProtectedEndpoint(HttpMethod method, String pathPattern, Permission... anyOf) {
        this.method = method;
        this.pathPattern = pathPattern;
        this.anyOf = List.of(anyOf);
    }

    public HttpMethod method() { return method; }

    public String pathPattern() { return pathPattern; }

    /** Chỉ cần có MỘT trong các quyền này là được gọi API. */
    public List<Permission> anyOf() { return anyOf; }

    String[] authorities() {
        return anyOf.stream().map(Enum::name).toArray(String[]::new);
    }

    /** Đường dẫn cụ thể để thử (thay * bằng giá trị mẫu). */
    public String samplePath() {
        return pathPattern.replace("*", "1");
    }

    /** Một quyền KHÔNG thuộc danh sách được phép — dùng để kiểm tra bị chặn. */
    public Permission anyOtherPermission() {
        return Arrays.stream(Permission.values()).filter(p -> !anyOf.contains(p)).findFirst().orElseThrow();
    }
}