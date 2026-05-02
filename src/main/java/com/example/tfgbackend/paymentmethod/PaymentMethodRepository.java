package com.example.tfgbackend.paymentmethod;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findByUserId(Long userId);

    Optional<PaymentMethod> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndCardTypeAndLast4(Long userId, CardType cardType, String last4);

    Optional<PaymentMethod> findFirstByUserIdAndIsDefaultTrue(Long userId);

    Optional<PaymentMethod> findFirstByUserIdAndIdNotOrderByCreatedAtDesc(Long userId, Long id);
}
