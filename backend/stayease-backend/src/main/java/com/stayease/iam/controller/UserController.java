package com.stayease.iam.controller;

import com.stayease.iam.dto.UserRequest;
import com.stayease.iam.dto.UserResponse;
import com.stayease.iam.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for users. This is the "front door" of the module.
 *
 * @RestController = this class handles web requests and its return values are
 * serialized straight to JSON (no view/template).
 * @RequestMapping("/api/users") = every endpoint here starts with that path.
 *
 * The controller stays THIN: it receives the request, delegates to the service,
 * and wraps the result in the right HTTP status. No business logic lives here.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** POST /api/users — create a user. Returns 201 Created with the new user. */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** GET /api/users — list all users. Returns 200 OK. */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userService.getAll());
    }

    /**
     * GET /api/users/managers — the list of PROPERTY_MANAGER users, used by an
     * owner to pick a manager for a property. Declared before /{id} so the
     * literal path wins over the id pattern.
     */
    @GetMapping("/managers")
    public ResponseEntity<List<UserResponse>> getManagers() {
        return ResponseEntity.ok(userService.getManagers());
    }

    /** GET /api/users/{id} — fetch one user. 200 OK, or 404 if not found. */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    /** PUT /api/users/{id} — update a user. 200 OK, or 404 if not found. */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    /** DELETE /api/users/{id} — delete a user. Returns 204 No Content. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
