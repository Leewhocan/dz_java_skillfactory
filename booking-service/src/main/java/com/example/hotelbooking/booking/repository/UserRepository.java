package com.example.hotelbooking.booking.repository;

import com.example.hotelbooking.booking.domain.User;
import com.example.hotelbooking.booking.domain.UserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(UserRole role);
}
