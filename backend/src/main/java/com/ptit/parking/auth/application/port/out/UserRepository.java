package com.ptit.parking.auth.application.port.out;

import com.ptit.parking.auth.domain.User;
import java.util.Optional;

/**
 * Port (cổng ra): tầng ứng dụng chỉ biết "tìm được người dùng", KHÔNG biết dữ liệu nằm ở MySQL,
 * file hay bộ nhớ. Cài đặt thật nằm ở adapter/out/persistence (Dependency Inversion — chữ D trong SOLID).
 * <p>Trả về kiểu cha {@link User}: đối tượng thật là Student / Guard / Manager / Owner.
 */
public interface UserRepository {

    Optional<User> findByUsername(String username);

    Optional<User> findById(long id);
}
