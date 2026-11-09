package com.example.hotelbooking.booking.mapper;

import com.example.hotelbooking.booking.domain.Booking;
import com.example.hotelbooking.booking.dto.BookingResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookingMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "username", source = "user.username")
    BookingResponse toResponse(Booking booking);

    List<BookingResponse> toResponse(List<Booking> bookings);
}
