package com.example.tfgbackend.gym;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymRepository extends JpaRepository<Gym, Long> {
    Page<Gym> findByActiveTrue(Pageable pageable);
}
