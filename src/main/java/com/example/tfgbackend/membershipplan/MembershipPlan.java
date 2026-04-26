package com.example.tfgbackend.membershipplan;

import com.example.tfgbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "membership_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipPlan extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_monthly", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceMonthly;

    /** Null means the plan grants unlimited classes per month. */
    @Column(name = "classes_per_month")
    private Integer classesPerMonth;

    @Column(name = "allows_waitlist", nullable = false)
    @Builder.Default
    private boolean allowsWaitlist = true;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
