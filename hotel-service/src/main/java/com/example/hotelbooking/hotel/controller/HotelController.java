package com.example.hotelbooking.hotel.controller;

import com.example.hotelbooking.hotel.dto.HotelRequest;
import com.example.hotelbooking.hotel.dto.HotelResponse;
import com.example.hotelbooking.hotel.service.HotelService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HotelResponse create(@RequestBody HotelRequest request) {
        return hotelService.create(request);
    }

    @GetMapping
    public List<HotelResponse> findAll() {
        return hotelService.findAll();
    }
}
