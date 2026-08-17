package com.streamhub.platform.common.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Common base for every domain entity in the system.
 * <p>
 * Provides: a random UUID primary key, automatic created/updated timestamps,
 * and a soft-delete flag. Every entity except {@code User} extends this
 * class (User has its own identity shape: internal numeric id + public uid).
 * <p>
 * {@link SQLRestriction} transparently filters out soft-deleted rows from
 * every query issued through Hibernate for subclasses of this entity, so
 * services never need to remember to add "deleted = false" themselves.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@MappedSuperclass
@SQLRestriction("deleted = false")
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    /** Marks this record as soft-deleted. Callers must still persist/save the entity. */
    public void markDeleted() {
        this.deleted = true;
    }
}
