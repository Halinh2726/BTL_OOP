package com.ptit.parking.payment.application.port.out;

import com.ptit.parking.payment.domain.Wallet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kho ví GIẢ trong bộ nhớ, dùng chung cho test của mọi use case cộng/trừ điểm (thay cho MySQL).
 * <p>Giống CSDL thật ở chỗ quan trọng nhất: đối tượng trả ra là BẢN SAO — đổi số dư mà quên {@link #save}
 * thì kho không đổi. "Khóa" không chặn gì nhưng ghi lại THỨ TỰ khóa để test kiểm tra.
 * Các hàm {@code synchronized} để test nhiều luồng dùng được.
 */
public class InMemoryWalletRepository implements WalletRepository {

    private final Map<Long, Wallet> byId = new LinkedHashMap<>();
    private final List<Long> lockOrder = new ArrayList<>();
    private long nextId = 1;

    @Override
    public synchronized long insert(Wallet wallet) {
        long id = nextId++;
        byId.put(id, new Wallet(id, wallet.userId(), wallet.balance()));
        return id;
    }

    @Override
    public synchronized Optional<Wallet> findByUserId(long userId) {
        return byId.values().stream().filter(w -> w.userId() == userId).findFirst().map(InMemoryWalletRepository::copy);
    }

    @Override
    public synchronized Wallet lockById(long walletId) {
        lockOrder.add(walletId);
        return copy(require(walletId));
    }

    @Override
    public synchronized Map<Long, Wallet> lockInIdOrder(Collection<Long> walletIds) {
        Map<Long, Wallet> locked = new LinkedHashMap<>();
        walletIds.stream().distinct().sorted().forEach(id -> locked.put(id, lockById(id)));
        return locked;
    }

    @Override
    public synchronized void save(Wallet wallet) {
        require(wallet.id());
        byId.put(wallet.id(), copy(wallet));
    }

    /** Số dư đang LƯU trong kho (sau các lần save). */
    public synchronized long balanceOf(long walletId) {
        return require(walletId).balance();
    }

    public synchronized List<Long> lockOrder() {
        return List.copyOf(lockOrder);
    }

    private Wallet require(long walletId) {
        Wallet wallet = byId.get(walletId);
        if (wallet == null) {
            throw new IllegalStateException("Không có ví id=" + walletId);
        }
        return wallet;
    }

    private static Wallet copy(Wallet wallet) {
        return new Wallet(wallet.id(), wallet.userId(), wallet.balance());
    }
}