package com.example.hotelbooking.booking.client;

import com.example.hotelbooking.booking.client.dto.RoomAvailabilityDto;
import com.example.hotelbooking.booking.client.dto.RoomBlockDto;
import com.example.hotelbooking.booking.client.dto.RoomDto;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "hotel-service", configuration = HotelClientConfiguration.class)
public interface HotelClient {

    @GetMapping("/api/rooms/recommend")
    List<RoomDto> recommendRooms();

    @PostMapping("/api/rooms/{id}/confirm-availability")
    RoomAvailabilityDto confirmAvailability(@PathVariable("id") Long roomId,
                                            @RequestBody RoomBlockDto period);

    @PostMapping("/api/rooms/{id}/release")
    void releaseRoom(@PathVariable("id") Long roomId,
                     @RequestBody RoomBlockDto period);
}
