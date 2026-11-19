package com.ptit.parking.shared.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpMethod;

import static com.ptit.parking.shared.security.Permission.ACCOUNT_MANAGE;
import static com.ptit.parking.shared.security.Permission.CASH_DEPOSIT;
import static com.ptit.parking.shared.security.Permission.CASH_WITHDRAWAL;
import static com.ptit.parking.shared.security.Permission.GUARD_CREATE;
import static com.ptit.parking.shared.security.Permission.GUARD_LOCK;
import static com.ptit.parking.shared.security.Permission.INCIDENT_REPORT;
import static com.ptit.parking.shared.security.Permission.OWNER_FINANCE;
import static com.ptit.parking.shared.security.Permission.POINT_ADJUST;
import static com.ptit.parking.shared.security.Permission.PARKING_CHECK_IN;
import static com.ptit.parking.shared.security.Permission.PARKING_CHECK_OUT;
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
    MY_TRANSACTIONS    (HttpMethod.GET,   "/api/v1/me/transactions",          WALLET_VIEW_OWN),    // #8
    CHECK_IN_PREVIEW   (HttpMethod.GET,   "/api/v1/guard/check-in/preview",   PARKING_CHECK_IN),   // #12
    CHECK_IN           (HttpMethod.POST,  "/api/v1/guard/check-in",           PARKING_CHECK_IN),   // #13
    CHECK_OUT_PREVIEW  (HttpMethod.GET,   "/api/v1/guard/check-out/preview",  PARKING_CHECK_OUT),  // #14
    CHECK_OUT          (HttpMethod.POST,  "/api/v1/guard/check-out",          PARKING_CHECK_OUT),  // #15
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
    AUDIT_LOGS         (HttpMethod.GET,   "/api/v1/manager/audit-logs",       ACCOUNT_MANAGE);     // Đợt 1

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