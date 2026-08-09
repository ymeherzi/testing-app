package com.tengames.auth.verification;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    Optional<TrustedDevice> findByTokenHash(String tokenHash);
}
