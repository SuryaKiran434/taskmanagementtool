package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.ProjectDTO;
import com.suryakiran.taskmanagementtool.dto.ProjectMemberDTO;
import com.suryakiran.taskmanagementtool.exception.ResourceNotFoundException;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.*;
import com.suryakiran.taskmanagementtool.repository.ProjectMemberRepository;
import com.suryakiran.taskmanagementtool.repository.ProjectRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(ProjectServiceImpl.class);

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    public ProjectServiceImpl(ProjectRepository projectRepository,
                               ProjectMemberRepository projectMemberRepository,
                               UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectDTO createProject(ProjectDTO dto, Authentication authentication) {
        User owner = getUser(authentication.getName());
        Project project = new Project();
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setColor(dto.getColor());
        project.setOwner(owner);
        Project saved = projectRepository.save(project);

        // Auto-add owner as OWNER member
        ProjectMember ownerMember = new ProjectMember();
        ownerMember.setProject(saved);
        ownerMember.setUser(owner);
        ownerMember.setRole(ProjectMemberRole.OWNER);
        projectMemberRepository.save(ownerMember);

        logger.info("Project created: {} by user: {}", saved.getId(), owner.getEmail());
        return toDTO(saved);
    }

    public List<ProjectDTO> getMyProjects(Authentication authentication) {
        User user = getUser(authentication.getName());
        return projectRepository.findAllAccessibleByUser(user).stream().map(this::toDTO).toList();
    }

    public ProjectDTO getProjectById(Long id, Authentication authentication) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        assertAccess(project, authentication);
        return toDTOWithMembers(project);
    }

    @Transactional
    public ProjectDTO updateProject(Long id, ProjectDTO dto, Authentication authentication) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        assertOwnerOrAdmin(project, authentication);
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setColor(dto.getColor());
        return toDTO(projectRepository.save(project));
    }

    @Transactional
    public void deleteProject(Long id, Authentication authentication) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        assertOwnerOrAdmin(project, authentication);
        projectRepository.delete(project);
    }

    @Transactional
    public ProjectMemberDTO addMember(Long projectId, int userId, ProjectMemberRole role, Authentication authentication) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        assertOwnerOrAdmin(project, authentication);
        User user = userRepository.findById((long) userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (projectMemberRepository.existsByProjectAndUser(project, user)) {
            throw new IllegalArgumentException("User is already a member of this project");
        }
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setRole(role != null ? role : ProjectMemberRole.VIEWER);
        ProjectMember saved = projectMemberRepository.save(member);
        return toMemberDTO(saved);
    }

    @Transactional
    public void removeMember(Long projectId, int userId, Authentication authentication) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        assertOwnerOrAdmin(project, authentication);
        User user = userRepository.findById((long) userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        projectMemberRepository.deleteByProjectAndUser(project, user);
    }

    private void assertAccess(Project project, Authentication authentication) {
        User user = getUser(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOwner = project.getOwner().getId() == user.getId();
        boolean isMember = projectMemberRepository.existsByProjectAndUser(project, user);
        if (!isAdmin && !isOwner && !isMember) {
            throw new ResourceNotFoundException("Project not found");
        }
    }

    private void assertOwnerOrAdmin(Project project, Authentication authentication) {
        User user = getUser(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && project.getOwner().getId() != user.getId()) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private ProjectDTO toDTO(Project project) {
        ProjectDTO dto = new ProjectDTO();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setColor(project.getColor());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        if (project.getOwner() != null) {
            dto.setOwnerId(project.getOwner().getId());
            dto.setOwnerName(project.getOwner().getFirstName() + " " + project.getOwner().getLastName());
        }
        dto.setTaskCount(project.getTasks() != null ? project.getTasks().size() : 0);
        return dto;
    }

    private ProjectDTO toDTOWithMembers(Project project) {
        ProjectDTO dto = toDTO(project);
        List<ProjectMember> members = projectMemberRepository.findByProject(project);
        dto.setMembers(members.stream().map(this::toMemberDTO).toList());
        return dto;
    }

    private ProjectMemberDTO toMemberDTO(ProjectMember member) {
        ProjectMemberDTO dto = new ProjectMemberDTO();
        dto.setId(member.getId());
        dto.setRole(member.getRole());
        if (member.getUser() != null) {
            dto.setUserId(member.getUser().getId());
            dto.setUserFullName(member.getUser().getFirstName() + " " + member.getUser().getLastName());
            dto.setUserEmail(member.getUser().getEmail());
            dto.setUserAvatarUrl(member.getUser().getAvatarUrl());
        }
        return dto;
    }
}
