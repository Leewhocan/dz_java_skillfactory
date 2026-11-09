package com.example.hotelbooking.hotel.controller;

import com.example.hotelbooking.hotel.dto.RoomAvailabilityResponse;
import com.example.hotelbooking.hotel.dto.RoomBlockRequest;
import com.example.hotelbooking.hotel.dto.RoomRequest;
import com.example.hotelbooking.hotel.dto.RoomResponse;
import com.example.hotelbooking.hotel.service.RoomService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@RequestBody RoomRequest request) {
        return roomService.create(request);
    }

    @GetMapping
    public List<RoomResponse> findAllAvailable() {
        return roomService.findAllAvailable();
    }

    @GetMapping("/recommend")
    public List<RoomResponse> recommend() {
        return roomService.recommend();
    }

    @PostMapping("/{id}/confirm-availability")
    public RoomAvailabilityResponse confirmAvailability(@PathVariable("id") Long id,
                                                        @RequestBody RoomBlockRequest request) {
        return roomService.confirmAvailability(id, request);
    }

    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable("id") Long id, @RequestBody RoomBlockRequest request) {
        roomService.release(id, request);
    }
}
