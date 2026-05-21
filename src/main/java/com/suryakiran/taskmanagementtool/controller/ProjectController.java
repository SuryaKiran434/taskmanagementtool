package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.dto.ProjectDTO;
import com.suryakiran.taskmanagementtool.dto.ProjectMemberDTO;
import com.suryakiran.taskmanagementtool.model.ProjectMemberRole;
import com.suryakiran.taskmanagementtool.service.ProjectServiceImpl;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectServiceImpl projectService;

    public ProjectController(ProjectServiceImpl projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectDTO> createProject(@Valid @RequestBody ProjectDTO dto,
                                                     Authentication authentication) {
        logger.info("Creating project: {}", dto.getName());
        return ResponseEntity.ok(projectService.createProject(dto, authentication));
    }

    @GetMapping
    public ResponseEntity<List<ProjectDTO>> getMyProjects(Authentication authentication) {
        return ResponseEntity.ok(projectService.getMyProjects(authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectDTO> getProject(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(projectService.getProjectById(id, authentication));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectDTO> updateProject(@PathVariable Long id,
                                                     @Valid @RequestBody ProjectDTO dto,
                                                     Authentication authentication) {
        return ResponseEntity.ok(projectService.updateProject(id, dto, authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id, Authentication authentication) {
        projectService.deleteProject(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ProjectMemberDTO> addMember(@PathVariable Long id,
                                                       @RequestParam int userId,
                                                       @RequestParam(required = false) ProjectMemberRole role,
                                                       Authentication authentication) {
        return ResponseEntity.ok(projectService.addMember(id, userId, role, authentication));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable int userId,
                                              Authentication authentication) {
        projectService.removeMember(id, userId, authentication);
        return ResponseEntity.noContent().build();
    }
}
