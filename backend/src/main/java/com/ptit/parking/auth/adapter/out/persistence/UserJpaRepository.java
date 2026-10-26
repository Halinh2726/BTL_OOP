package com.ptit.parking.auth.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data tự sinh câu SQL từ tên phương thức. Chỉ dùng BÊN TRONG adapter persistence. */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByUsername(String username);

    @Query(value = "SELECT permission_code FROM role_permissions WHERE role_code = :roleCode", nativeQuery = true)
    List<String> findPermissionCodesByRole(@Param("roleCode") String roleCode);
}
