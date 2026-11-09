package com.example.hotelbooking.booking.repository;

import com.example.hotelbooking.booking.domain.Booking;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<Booking> findById(Long id);

    @EntityGraph(attributePaths = "user")
    Optional<Booking> findByRequestId(String requestId);

    @EntityGraph(attributePaths = "user")
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserId(Long userId);
}
