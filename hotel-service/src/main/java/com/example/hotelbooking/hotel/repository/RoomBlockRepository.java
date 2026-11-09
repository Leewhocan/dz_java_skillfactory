package com.example.hotelbooking.hotel.repository;

import com.example.hotelbooking.hotel.domain.RoomBlock;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomBlockRepository extends JpaRepository<RoomBlock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from RoomBlock b
             where b.roomId = :roomId
               and b.startDate < :endDate
               and b.endDate > :startDate
            """)
    List<RoomBlock> findOverlappingForUpdate(@Param("roomId") Long roomId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);
}
