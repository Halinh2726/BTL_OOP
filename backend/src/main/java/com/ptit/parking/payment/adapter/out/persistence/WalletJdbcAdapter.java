package com.ptit.parking.payment.adapter.out.persistence;

import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Cài đặt {@link WalletRepository} bằng SQL thuần (JdbcClient); khóa dòng bằng SELECT ... FOR UPDATE của InnoDB. */
@Component
class WalletJdbcAdapter implements WalletRepository {

    private final JdbcClient jdbc;

    WalletJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long insert(Wallet wallet) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO wallets (user_id, balance) VALUES (:userId, :balance)")
                .param("userId", wallet.userId())
                .param("balance", wallet.balance())
                .update(keys);
        return keys.getKey().longValue();
    }

    @Override
    public Optional<Wallet> findByUserId(long userId) {
        return jdbc.sql("SELECT id, user_id, balance FROM wallets WHERE user_id = :userId")
                .param("userId", userId)
                .query(WalletJdbcAdapter::toWallet)
                .optional();
    }

    @Override
    public Wallet lockById(long walletId) {
        return jdbc.sql("SELECT id, user_id, balance FROM wallets WHERE id = :id FOR UPDATE")
                .param("id", walletId)
                .query(WalletJdbcAdapter::toWallet)
                .single();
    }

    @Override
    public Map<Long, Wallet> lockInIdOrder(Collection<Long> walletIds) {
        Map<Long, Wallet> locked = new LinkedHashMap<>();
        walletIds.stream().distinct().sorted().forEach(id -> locked.put(id, lockById(id)));
        return locked;
    }

    @Override
    public void save(Wallet wallet) {
        int updated = jdbc.sql("UPDATE wallets SET balance = :balance WHERE id = :id")
                .param("balance", wallet.balance())
                .param("id", wallet.id())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Không tìm thấy ví id=" + wallet.id());
        }
    }

    private static Wallet toWallet(ResultSet rs, int row) throws SQLException {
        return new Wallet(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("balance"));
    }
}