package com.stayease.iam.service;

import com.stayease.common.exception.DuplicateResourceException;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.iam.dto.UserRequest;
import com.stayease.iam.dto.UserResponse;
import com.stayease.iam.entity.User;
import com.stayease.iam.enums.UserRole;
import com.stayease.iam.mapper.UserMapper;
import com.stayease.iam.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The actual business logic for users.
 *
 * @Service marks this as a Spring-managed bean so it can be injected into the
 * controller. @Transactional means each public method runs inside a database
 * transaction: if it throws, everything rolls back (all-or-nothing).
 *
 * The UserRepository is supplied through CONSTRUCTOR INJECTION — Spring sees
 * the constructor needs a UserRepository and passes one in automatically. This
 * is preferred over field injection because it makes dependencies explicit and
 * the class easy to unit-test.
 */
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserResponse create(UserRequest request) {
        // Business rule: email must be unique. We check here for a friendly 409
        // (the DB unique constraint is the final safety net).
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "A user already exists with email " + request.email());
        }
        User saved = userRepository.save(UserMapper.toEntity(request));
        return UserMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true) // read-only is a small performance hint to JPA
    public List<UserResponse> getAll() {
        return userRepository.findAll()
                .stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getManagers() {
        return userRepository.findByRole(UserRole.PROPERTY_MANAGER)
                .stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)// keeps the session open in lazy load
    public UserResponse getById(Long id) {
        User user = findUserOrThrow(id);
        return UserMapper.toResponse(user);
    }

    @Override
    public UserResponse update(Long id, UserRequest request) {
        User user = findUserOrThrow(id);

        // If the email is being changed, make sure the new one isn't taken.
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(request.email());
        if (emailChanged && userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "A user already exists with email " + request.email());
        }

        UserMapper.updateEntity(user, request);//to update the entity
        // 'user' is already managed by JPA inside this transaction, so changes
        // are flushed automatically; calling save() makes the intent explicit.
        return UserMapper.toResponse(userRepository.save(user));
    }

    @Override
    public void delete(Long id) {
        User user = findUserOrThrow(id);
        userRepository.delete(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return id != null && userRepository.existsById(id);
    }

    /** Shared lookup that throws a 404-mapping exception when id is unknown. */
    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id " + id));
    }
}
