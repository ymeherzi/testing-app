package com.prono10.auth.verification;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthCodeRepository extends JpaRepository<AuthCode, Long> {

    Optional<AuthCode> findFirstByUserIdAndPurposeOrderByIdDesc(Long userId, AuthCode.Purpose purpose);
}
