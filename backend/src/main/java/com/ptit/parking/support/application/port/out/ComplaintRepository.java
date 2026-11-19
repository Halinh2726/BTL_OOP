package com.ptit.parking.support.application.port.out;

import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.support.domain.Complaint;
import com.ptit.parking.support.domain.ComplaintStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Port: khiếu nại (complaints) và ảnh đính kèm (complaint_photos — dùng chung kiểu ảnh đã kiểm tra với sự cố). */
public interface ComplaintRepository {

    /** Lưu khiếu nại MỚI kèm ảnh (nếu có) — gọi trong transaction. */
    Complaint insert(Complaint complaint, IncidentPhoto photo);

    /** Ghi lại trạng thái và các trường xử lý sau mỗi hành vi. */
    void update(Complaint complaint);

    Optional<Complaint> findById(long id);

    /** SELECT ... FOR UPDATE: hai người bấm cùng lúc thì người sau đợi và thấy trạng thái mới. */
    Optional<Complaint> lockById(long id);

    List<Complaint> findByStudent(long studentId, int limit);

    /** Theo các trạng thái (rỗng = mọi trạng thái), mới nhất trước. */
    List<Complaint> findByStatuses(Collection<ComplaintStatus> statuses, int limit);

    Optional<IncidentPhoto> findPhoto(long complaintId);
}
