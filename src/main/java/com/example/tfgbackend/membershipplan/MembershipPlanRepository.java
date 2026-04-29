package com.example.tfgbackend.membershipplan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {
    Page<MembershipPlan> findByActiveTrue(Pageable pageable);
    Page<MembershipPlan> findByActiveFalse(Pageable pageable);
}
