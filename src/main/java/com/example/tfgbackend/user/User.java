package com.example.tfgbackend.user;

import com.example.tfgbackend.common.BaseEntity;
import com.example.tfgbackend.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A gym member, instructor, or admin account.
 *
 * <p>The underlying table is named {@code app_user} because {@code user} is a reserved
 * word in PostgreSQL.
 *
 * <p>PII note: {@code email}, {@code name}, and {@code phone} are personal data —
 * apply a data-retention policy before going to production.
 *
 * <p>{@code passwordHash} stores a BCrypt digest; never store plaintext.
 */
@Entity
@Table(
    name = "app_user",
    uniqueConstraints = @UniqueConstraint(name = "uq_app_user_email", columnNames = "email")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "phone", length = 15)
    private String phone;

    /** BCrypt hash of the user's password.  Never expose over the wire. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.CUSTOMER;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
