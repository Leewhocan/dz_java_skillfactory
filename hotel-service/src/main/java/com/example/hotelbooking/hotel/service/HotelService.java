package com.example.hotelbooking.hotel.service;

import com.example.hotelbooking.hotel.domain.Hotel;
import com.example.hotelbooking.hotel.dto.HotelRequest;
import com.example.hotelbooking.hotel.dto.HotelResponse;
import com.example.hotelbooking.hotel.exception.BadRequestException;
import com.example.hotelbooking.hotel.mapper.HotelMapper;
import com.example.hotelbooking.hotel.repository.HotelRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotelService {

    private final HotelRepository hotelRepository;
    private final HotelMapper hotelMapper;

    @Transactional
    public HotelResponse create(HotelRequest request) {
        requireText(request.name(), "Hotel name must not be blank");
        requireText(request.address(), "Hotel address must not be blank");

        Hotel saved = hotelRepository.save(hotelMapper.toEntity(request));
        return hotelMapper.toResponse(saved);
    }

    public List<HotelResponse> findAll() {
        return hotelRepository.findAll().stream()
                .map(hotelMapper::toResponse)
                .toList();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
    }
}
