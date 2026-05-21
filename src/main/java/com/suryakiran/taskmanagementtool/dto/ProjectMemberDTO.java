package com.suryakiran.taskmanagementtool.dto;

import com.suryakiran.taskmanagementtool.model.ProjectMemberRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectMemberDTO {
    private Long id;
    private int userId;
    private String userFullName;
    private String userEmail;
    private String userAvatarUrl;
    private ProjectMemberRole role;
}
