package com.example.tfgbackend.gym;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GymRepository extends JpaRepository<Gym, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    // Filters are applied only when the parameter is non-null; q matches name or address.
    // CAST(:param AS string) is required to avoid Hibernate 6 binding null params as bytea,
    // which causes PostgreSQL to fail with "function lower(bytea) does not exist".
    @Query("""
            SELECT g FROM Gym g
            WHERE (:city IS NULL OR LOWER(g.city) = LOWER(CAST(:city AS string)))
              AND (:active IS NULL OR g.active = :active)
              AND (:q IS NULL
                   OR LOWER(g.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(g.address) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """)
    Page<Gym> findByFilters(
            @Param("city") String city,
            @Param("active") Boolean active,
            @Param("q") String q,
            Pageable pageable);
}
