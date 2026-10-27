package com.ptit.parking.payment.adapter.out.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.Wallet;

/** Cài đặt {@link WalletRepository} bằng SQL thuần (JdbcClient). */
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
}