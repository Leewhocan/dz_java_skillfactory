package com.example.hotelbooking.hotel.repository;

import com.example.hotelbooking.hotel.domain.Room;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Override
    @EntityGraph(attributePaths = "hotel")
    Optional<Room> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "hotel")
    List<Room> findByAvailableTrue();

    @EntityGraph(attributePaths = "hotel")
    List<Room> findByAvailableTrueOrderByTimesBookedAscIdAsc();
}
