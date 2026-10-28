package com.ptit.parking.manager.application.port.out;

import com.ptit.parking.auth.domain.Guard;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Bản giả trong bộ nhớ của {@link GuardAccountRepository} cho test use case. Lưu và trả về BẢN SAO,
 * giống CSDL thật: use case đổi trạng thái mà quên gọi saveStatus thì test sẽ thấy.
 */
public class InMemoryGuardAccountRepository implements GuardAccountRepository {

    private final Map<Long, Guard> rows = new TreeMap<>();
    private int statusWrites;

    @Override
    public boolean usernameExists(String username) {
        return rows.values().stream().anyMatch(g -> g.username().equals(username));
    }

    @Override
    public Guard save(Guard guard) {
        Guard withId = copy(guard, (long) rows.size() + 1);
        rows.put(withId.id(), withId);
        return copy(withId, withId.id());
    }

    @Override
    public List<Guard> findAll() {
        return rows.values().stream()
                .sorted(Comparator.comparing(Guard::fullName))
                .map(g -> copy(g, g.id()))
                .toList();
    }

    @Override
    public Optional<Guard> findById(long userId) {
        return Optional.ofNullable(rows.get(userId)).map(g -> copy(g, g.id()));
    }

    @Override
    public void saveStatus(Guard guard) {
        rows.put(guard.id(), copy(guard, guard.id()));
        statusWrites++;
    }

    /** Thêm sẵn một bảo vệ (bất kỳ trạng thái); trả về id. */
    public long insert(Guard guard) {
        return save(guard).id();
    }

    /** Trạng thái ĐÃ LƯU của bảo vệ. */
    public Guard stored(long id) {
        return rows.get(id);
    }

    public List<Guard> all() {
        return List.copyOf(rows.values());
    }

    public int statusWrites() {
        return statusWrites;
    }

    private static Guard copy(Guard g, Long id) {
        return new Guard(id, g.username(), g.passwordHash(), g.fullName(), g.status(), g.phone());
    }
}
