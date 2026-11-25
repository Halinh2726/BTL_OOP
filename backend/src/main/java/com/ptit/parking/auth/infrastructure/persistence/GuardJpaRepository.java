package com.ptit.parking.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface GuardJpaRepository extends JpaRepository<GuardJpaEntity, Long> {
}
