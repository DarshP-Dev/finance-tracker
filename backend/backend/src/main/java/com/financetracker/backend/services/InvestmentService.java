package com.financetracker.backend.services;

import com.financetracker.backend.dto.InvestmentHoldingResponse;
import com.financetracker.backend.dto.InvestmentRequest;
import com.financetracker.backend.dto.InvestmentResponse;
import com.financetracker.backend.entities.Investment;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.InvestmentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class InvestmentService {

    private static final int MAX_TICKER_LENGTH = 20;
    private static final int AVERAGE_PRICE_SCALE = 4;

    private final AuthenticatedUserService authenticatedUserService;
    private final InvestmentRepository investmentRepository;

    @Transactional(readOnly = true)
    public List<InvestmentResponse> getInvestments(Authentication authentication, String ticker) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        List<Investment> investments = ticker == null || ticker.isBlank()
                ? investmentRepository.findByUserIdOrderByPurchaseDateDesc(user.getId())
                : investmentRepository.findByUserIdAndTickerOrderByPurchaseDateDesc(
                        user.getId(), normalizeTicker(ticker));

        return investments.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InvestmentHoldingResponse> getHoldings(Authentication authentication) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        Map<String, HoldingAccumulator> holdings = new TreeMap<>();

        for (Investment investment : investmentRepository.findByUserIdOrderByPurchaseDateDesc(user.getId())) {
            holdings.computeIfAbsent(investment.getTicker(), ignored -> new HoldingAccumulator())
                    .add(investment);
        }

        return holdings.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .toList();
    }

    @Transactional
    public InvestmentResponse createInvestment(Authentication authentication, InvestmentRequest request) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        ValidatedInvestment validated = validate(request);

        Investment investment = Investment.builder()
                .user(user)
                .ticker(validated.ticker())
                .shares(validated.shares())
                .purchasePrice(validated.purchasePrice())
                .purchaseDate(validated.purchaseDate())
                .build();

        return toResponse(investmentRepository.save(investment));
    }

    @Transactional
    public InvestmentResponse updateInvestment(
            Authentication authentication,
            Long investmentId,
            InvestmentRequest request
    ) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        Investment investment = getOwnedInvestment(investmentId, user.getId());
        ValidatedInvestment validated = validate(request);

        investment.setTicker(validated.ticker());
        investment.setShares(validated.shares());
        investment.setPurchasePrice(validated.purchasePrice());
        investment.setPurchaseDate(validated.purchaseDate());

        return toResponse(investmentRepository.save(investment));
    }

    @Transactional
    public void deleteInvestment(Authentication authentication, Long investmentId) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        investmentRepository.delete(getOwnedInvestment(investmentId, user.getId()));
    }

    private Investment getOwnedInvestment(Long investmentId, Long userId) {
        return investmentRepository.findByIdAndUserId(investmentId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investment not found"));
    }

    private ValidatedInvestment validate(InvestmentRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Investment details are required");
        }

        String ticker = normalizeTicker(request.getTicker());
        if (ticker.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticker cannot be empty");
        }
        if (ticker.length() > MAX_TICKER_LENGTH || !ticker.matches("[A-Z0-9][A-Z0-9.-]*")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ticker may contain up to 20 letters, numbers, periods, and hyphens"
            );
        }

        BigDecimal shares = requirePositive(request.getShares(), "Shares must be greater than 0");
        BigDecimal purchasePrice = requirePositive(
                request.getPurchasePrice(), "Purchase price must be greater than 0");
        LocalDate purchaseDate = request.getPurchaseDate();
        if (purchaseDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase date is required");
        }
        if (purchaseDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase date cannot be in the future");
        }

        return new ValidatedInvestment(ticker, shares, purchasePrice, purchaseDate);
    }

    private String normalizeTicker(String ticker) {
        return ticker == null ? "" : ticker.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private InvestmentResponse toResponse(Investment investment) {
        return InvestmentResponse.builder()
                .id(investment.getId())
                .ticker(investment.getTicker())
                .shares(investment.getShares())
                .purchasePrice(investment.getPurchasePrice())
                .purchaseDate(investment.getPurchaseDate())
                .amountInvested(investment.getShares().multiply(investment.getPurchasePrice()))
                .createdAt(investment.getCreatedAt())
                .build();
    }

    private record ValidatedInvestment(
            String ticker,
            BigDecimal shares,
            BigDecimal purchasePrice,
            LocalDate purchaseDate
    ) {
    }

    private static final class HoldingAccumulator {

        private BigDecimal totalShares = BigDecimal.ZERO;
        private BigDecimal totalInvested = BigDecimal.ZERO;
        private long purchaseCount;

        void add(Investment investment) {
            totalShares = totalShares.add(investment.getShares());
            totalInvested = totalInvested.add(investment.getShares().multiply(investment.getPurchasePrice()));
            purchaseCount++;
        }

        InvestmentHoldingResponse toResponse(String ticker) {
            return InvestmentHoldingResponse.builder()
                    .ticker(ticker)
                    .totalShares(totalShares)
                    .totalInvested(totalInvested)
                    .averagePurchasePrice(totalInvested.divide(
                            totalShares, AVERAGE_PRICE_SCALE, RoundingMode.HALF_UP))
                    .purchaseCount(purchaseCount)
                    .build();
        }
    }
}
