package com.example.tfgbackend.classtype;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassTypeRepository extends JpaRepository<ClassType, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("""
            SELECT ct FROM ClassType ct
            WHERE (:level IS NULL OR ct.level = :level)
              AND (:q IS NULL OR LOWER(ct.name) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY ct.name ASC
            """)
    Page<ClassType> findByFilters(
            @Param("level") String level,
            @Param("q") String q,
            Pageable pageable);
}
