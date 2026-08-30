package com.suryakiran.taskmanagementtool.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for {@code PUT /api/users/{id}}.
 *
 * <p>This endpoint used to bind the JPA {@code User} entity straight from the request body.
 * Because Jackson populates whatever it finds, and {@code UserServiceImpl.updateUser} copies
 * a non-empty {@code roles} collection onto the persisted user, any caller allowed to edit a
 * profile — including a non-admin editing their <em>own</em> profile — could send
 * {@code "roles": [{"id": 1, "name": "ROLE_ADMIN"}]} and grant themselves administrator.
 * That is mass assignment (CWE-915); {@code id}, {@code createdAt} and {@code tasks} were
 * exposed by the same binding.</p>
 *
 * <p>Only the four fields below can now arrive from a client, and the controller copies them
 * across one at a time. A field this DTO does not declare has nowhere to land: Jackson has no
 * setter for it, so it cannot reach the entity however the JSON is shaped. Role changes keep
 * their own admin-only endpoints ({@code /assign-admin}, {@code /remove-admin}).</p>
 *
 * <p>Every field is optional — {@code updateUser} treats {@code null} as "leave unchanged",
 * so a partial update is legitimate. The constraints therefore validate a value only when one
 * is actually supplied.</p>
 */
@Getter
@Setter
public class UserUpdateDTO {

    @Size(max = 50, message = "First name must be at most 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name must be at most 50 characters")
    private String lastName;

    @Email(message = "Email should be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    /**
     * Plain-text replacement password. Left {@code null} to keep the current one; when
     * present it is checked by {@code UserValidationService} and encoded before storage.
     */
    private String password;
}
