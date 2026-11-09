package com.example.hotelbooking.hotel.mapper;

import com.example.hotelbooking.hotel.domain.Room;
import com.example.hotelbooking.hotel.dto.RoomRequest;
import com.example.hotelbooking.hotel.dto.RoomResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RoomMapper {

    @Mapping(target = "hotelId", source = "hotel.id")
    @Mapping(target = "hotelName", source = "hotel.name")
    RoomResponse toResponse(Room room);

    List<RoomResponse> toResponse(List<Room> rooms);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    @Mapping(target = "available", ignore = true)
    @Mapping(target = "timesBooked", ignore = true)
    Room toEntity(RoomRequest request);
}
