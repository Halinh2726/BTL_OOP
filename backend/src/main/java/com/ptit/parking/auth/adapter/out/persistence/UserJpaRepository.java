package com.ptit.parking.auth.adapter.out.persistence;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptit.parking.auth.domain.AccountStatus;

/** Spring Data tự sinh câu SQL từ tên phương thức. Chỉ dùng BÊN TRONG adapter persistence. */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByUsername(String username);

    @Query(value = "SELECT permission_code FROM role_permissions WHERE role_code = :roleCode", nativeQuery = true)
    List<String> findPermissionCodesByRole(@Param("roleCode") String roleCode);
    
    /** Chỉ đọc cột status — truy vấn nhẹ vì chạy ở MỌI request đã đăng nhập. */
    @Query("SELECT u.status FROM UserJpaEntity u WHERE u.id = :id")
    Optional<AccountStatus> findStatusById(@Param("id") long id);
}
