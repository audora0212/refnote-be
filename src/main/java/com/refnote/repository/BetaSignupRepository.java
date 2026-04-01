package com.refnote.repository;

import com.refnote.entity.BetaSignup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BetaSignupRepository extends JpaRepository<BetaSignup, Long> {
    boolean existsByEmail(String email);
}
