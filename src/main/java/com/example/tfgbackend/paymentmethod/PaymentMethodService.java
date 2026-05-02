package com.example.tfgbackend.paymentmethod;

import com.example.tfgbackend.common.exception.CardExpiredException;
import com.example.tfgbackend.common.exception.DuplicatePaymentMethodException;
import com.example.tfgbackend.common.exception.InvalidDefaultToggleException;
import com.example.tfgbackend.common.exception.PaymentMethodNotFoundException;
import com.example.tfgbackend.paymentmethod.dto.AddPaymentMethodRequest;
import com.example.tfgbackend.paymentmethod.dto.PaymentMethodResponse;
import com.example.tfgbackend.paymentmethod.dto.SetDefaultRequest;
import com.example.tfgbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;

    @Transactional
    public PaymentMethodResponse addPaymentMethod(Long userId, AddPaymentMethodRequest req) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new PaymentMethodNotFoundException(userId));

        if (paymentMethodRepository.existsByUserIdAndCardTypeAndLast4(userId, req.cardType(), req.last4())) {
            throw new DuplicatePaymentMethodException();
        }

        YearMonth now = YearMonth.now();
        int currentYear  = now.getYear();
        int currentMonth = now.getMonthValue();
        // A card is expired if its year is in the past, or same year but month is in the past
        if (req.expiryYear() < currentYear
                || (req.expiryYear() == currentYear && req.expiryMonth() < currentMonth)) {
            throw new CardExpiredException();
        }

        // First card for this user is automatically set as default
        boolean isDefault = paymentMethodRepository.findByUserId(userId).isEmpty();

        PaymentMethod pm = PaymentMethod.builder()
                .cardType(req.cardType())
                .last4(req.last4())
                .expiryMonth(req.expiryMonth())
                .expiryYear(req.expiryYear())
                .cardholderName(req.cardholderName())
                .isDefault(isDefault)
                .user(user)
                .build();

        PaymentMethod saved = paymentMethodRepository.save(pm);
        return toResponse(saved);
    }

    public List<PaymentMethodResponse> listPaymentMethods(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new PaymentMethodNotFoundException(userId);
        }
        return paymentMethodRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public PaymentMethodResponse getPaymentMethod(Long userId, Long pmId) {
        return paymentMethodRepository.findByIdAndUserId(pmId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new PaymentMethodNotFoundException(pmId));
    }

    @Transactional
    public PaymentMethodResponse setDefault(Long userId, Long pmId, SetDefaultRequest req) {
        if (!req.isDefault()) {
            throw new InvalidDefaultToggleException();
        }

        PaymentMethod card = paymentMethodRepository.findByIdAndUserId(pmId, userId)
                .orElseThrow(() -> new PaymentMethodNotFoundException(pmId));

        // Clear any existing default for this user before promoting the new one
        paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(userId).ifPresent(old -> {
            old.setDefault(false);
            paymentMethodRepository.save(old);
        });

        card.setDefault(true);
        PaymentMethod saved = paymentMethodRepository.save(card);
        return toResponse(saved);
    }

    @Transactional
    public void deletePaymentMethod(Long userId, Long pmId) {
        PaymentMethod card = paymentMethodRepository.findByIdAndUserId(pmId, userId)
                .orElseThrow(() -> new PaymentMethodNotFoundException(pmId));

        if (card.isDefault()) {
            // Promote the most-recently-added sibling to default
            paymentMethodRepository.findFirstByUserIdAndIdNotOrderByCreatedAtDesc(userId, pmId)
                    .ifPresent(sibling -> {
                        sibling.setDefault(true);
                        paymentMethodRepository.save(sibling);
                    });
        }

        paymentMethodRepository.delete(card);
    }

    private PaymentMethodResponse toResponse(PaymentMethod pm) {
        return new PaymentMethodResponse(
                pm.getId(),
                pm.getCardType(),
                pm.getLast4(),
                pm.getExpiryMonth(),
                pm.getExpiryYear(),
                pm.getCardholderName(),
                pm.isDefault(),
                pm.getCreatedAt()
        );
    }
}
