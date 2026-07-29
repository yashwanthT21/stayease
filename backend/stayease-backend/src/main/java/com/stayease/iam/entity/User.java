package com.stayease.iam.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.stayease.iam.enums.UserRole;
import com.stayease.iam.enums.UserStatus;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Column(nullable = false, length = 180, unique = true)
    private String email;

    @Column(length = 20)
    private String phone;

    /**
     * BCrypt hash of the user's password (added in Flyway V2). Nullable because
     * legacy rows created before auth existed have none. This is never exposed
     * in any response DTO.
     */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

}
