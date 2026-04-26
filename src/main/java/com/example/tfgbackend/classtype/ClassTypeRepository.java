package com.example.tfgbackend.classtype;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassTypeRepository extends JpaRepository<ClassType, Long> {

    /** Find all class types for a given difficulty level (BASIC / INTERMEDIATE / ADVANCED). */
    List<ClassType> findByLevel(String level);

    /** Case-insensitive name search — useful for admin duplicate detection. */
    boolean existsByNameIgnoreCase(String name);
}
