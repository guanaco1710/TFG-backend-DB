package com.example.tfgbackend.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Shared persistence superclass.
 *
 * <p>All child entities inherit:
 * <ul>
 *   <li>A surrogate {@code BIGSERIAL} primary key.</li>
 *   <li>An optimistic-locking {@code version} column — the DB row must have this column.</li>
 *   <li>Spring Data auditing timestamps ({@code created_at}, {@code updated_at}).</li>
 * </ul>
 *
 * <p>Equals / hashCode are based solely on {@code id} so that Hibernate proxies,
 * detached instances, and freshly-constructed (pre-persist) instances all behave
 * correctly in collections without breaking Set semantics.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Optimistic-locking counter. All subclass tables must have a {@code version BIGINT} column
     * (added via Flyway migration V3 for tables that did not originally include one).
     */
    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
