package com.shiptrack.service;

import com.shiptrack.dto.RegisterRequest;
import com.shiptrack.dto.UserDto;
import com.shiptrack.entity.User;
import com.shiptrack.enums.Role;

import java.util.List;

public interface UserService {
    UserDto registerUser(RegisterRequest request);
    UserDto registerPublicUser(RegisterRequest request);
    UserDto registerPrivilegedUser(RegisterRequest request);
    List<UserDto> getAllUsers();
    List<UserDto> getUsersByRole(Role role);
    UserDto getCurrentUser();
    User getUserByEmail(String email);
    User getUserById(Long id);
}
