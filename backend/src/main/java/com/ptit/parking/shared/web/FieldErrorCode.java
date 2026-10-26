package com.ptit.parking.shared.web;

import java.util.Arrays;
import java.util.Set;

/**
 * Mã lỗi theo trường (Đặc tả API mục 5): gom nhiều annotation kiểm tra của Jakarta Validation
 * về 5 mã ổn định mà giao diện xử lý được. Mỗi hằng enum tự biết mình đại diện cho annotation nào.
 */
public enum FieldErrorCode {
    REQUIRED("NotNull", "NotBlank", "NotEmpty"),
    PATTERN("Pattern", "Email"),
    LENGTH("Size", "Length"),
    RANGE("Min", "Max", "Positive", "PositiveOrZero", "Negative", "DecimalMin", "DecimalMax", "Range"),
    NOT_FOUND;

    private final Set<String> constraints;

    FieldErrorCode(String... constraints) {
        this.constraints = Set.of(constraints);
    }

    /** {@code constraintName} là tên annotation, ví dụ "NotBlank". Không nhận ra thì coi là sai định dạng. */
    public static FieldErrorCode fromConstraint(String constraintName) {
        return Arrays.stream(values())
                .filter(code -> code.constraints.contains(constraintName))
                .findFirst()
                .orElse(PATTERN);
    }
}