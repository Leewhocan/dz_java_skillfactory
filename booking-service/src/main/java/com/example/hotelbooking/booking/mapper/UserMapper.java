package com.example.hotelbooking.booking.mapper;

import com.example.hotelbooking.booking.domain.User;
import com.example.hotelbooking.booking.dto.AdminUserRequest;
import com.example.hotelbooking.booking.dto.UserResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserResponse toResponse(User user);

    List<UserResponse> toResponse(List<User> users);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", ignore = true)
    User toEntity(AdminUserRequest request);
}
