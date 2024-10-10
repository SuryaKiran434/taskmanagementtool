package com.suryakiran.taskmanagementtool.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class UserDTO {
    private int id;
    private String firstName;
    private String lastName;
    private String email;
    private List<String> roles;
}