package com.ptit.parking.auth.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface GuardJpaRepository extends JpaRepository<GuardJpaEntity, Long> {
}
