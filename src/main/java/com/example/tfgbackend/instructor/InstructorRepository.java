package com.example.tfgbackend.instructor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstructorRepository extends JpaRepository<Instructor, Long> {

    /** Find instructors who teach a particular specialty (e.g. "Spinning"). */
    List<Instructor> findBySpecialty(String specialty);
}
