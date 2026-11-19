package com.ptit.parking.owner.application.port.out;

import com.ptit.parking.owner.domain.WithdrawalRequest;
import com.ptit.parking.owner.domain.WithdrawalStatus;
import java.util.List;
import java.util.Optional;

/** Port: yêu cầu rút điểm (withdrawal_requests). */
public interface WithdrawalRequestRepository {

    /** Chưa có id → thêm mới (trả về bản có id); đã có id → ghi trạng thái hiện tại. */
    WithdrawalRequest save(WithdrawalRequest request);

    Optional<WithdrawalRequest> findById(long id);

    /** KHÓA dòng tới hết transaction: quản lý bấm "Đã trả tiền" hai lần thì lần sau chờ rồi thấy đã trả. */
    Optional<WithdrawalRequest> lockById(long id);

    /** Mới nhất trước. {@code status} null = mọi trạng thái. */
    List<WithdrawalRequest> findByStatus(WithdrawalStatus status, int limit);

    List<WithdrawalRequest> findByOwner(long ownerId, int limit);

    /** R15: tổng số điểm của các yêu cầu đang chờ xử lý. */
    long sumPendingPoints(long ownerId);
}
