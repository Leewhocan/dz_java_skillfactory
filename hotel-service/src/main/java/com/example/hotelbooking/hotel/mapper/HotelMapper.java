package com.example.hotelbooking.hotel.mapper;

import com.example.hotelbooking.hotel.domain.Hotel;
import com.example.hotelbooking.hotel.dto.HotelRequest;
import com.example.hotelbooking.hotel.dto.HotelResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HotelMapper {

    HotelResponse toResponse(Hotel hotel);

    @Mapping(target = "id", ignore = true)
    Hotel toEntity(HotelRequest request);
}
