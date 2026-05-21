package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.UserDTO;
import com.suryakiran.taskmanagementtool.model.Role;
import com.suryakiran.taskmanagementtool.model.User;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for converting between User entities and UserDTOs.
 * Implements Factory pattern for better separation of concerns.
 */
@Service
public class UserConversionService {

    /**
     * Converts a User entity to UserDTO
     * @param user the user entity to convert
     * @return the converted UserDTO
     */
    public UserDTO convertToDTO(User user) {
        if (user == null) {
            return null;
        }

        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setFirstName(user.getFirstName());
        userDTO.setLastName(user.getLastName());
        userDTO.setEmail(user.getEmail());
        userDTO.setRoles(extractRoleNames(user.getRoles()));
        userDTO.setCreatedAt(user.getCreatedAt());
        userDTO.setAvatarUrl(user.getAvatarUrl());

        return userDTO;
    }

    /**
     * Converts a list of User entities to UserDTOs
     * @param users the list of user entities to convert
     * @return the list of converted UserDTOs
     */
    public List<UserDTO> convertToDTOList(List<User> users) {
        if (users == null) {
            return List.of();
        }

        return users.stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * Extracts role names from a set of Role entities
     * @param roles the set of roles
     * @return the list of role names
     */
    private List<String> extractRoleNames(java.util.Set<Role> roles) {
        if (roles == null) {
            return List.of();
        }

        return roles.stream()
                .map(Role::getName)
                .toList();
    }
}
