package com.ptit.parking.shared.application.port.out;

import java.util.function.Supplier;

/**
 * Port: chạy một khối công việc trong MỘT transaction — hoặc tất cả thành công, hoặc không gì được ghi.
 * Use case gọi interface này nên vẫn là Java thuần, không cần annotation {@code @Transactional} của Spring;
 * test thay bằng lớp giả để kiểm tra "việc gì nằm trong transaction".
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);
}