package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financetracker.backend.dto.InvestmentHoldingResponse;
import com.financetracker.backend.dto.InvestmentRequest;
import com.financetracker.backend.dto.InvestmentResponse;
import com.financetracker.backend.entities.Investment;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.InvestmentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InvestmentServiceTests {

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private Authentication authentication;

    private InvestmentService investmentService;
    private User user;

    @BeforeEach
    void setUp() {
        investmentService = new InvestmentService(authenticatedUserService, investmentRepository);
        user = User.builder().id(25L).email("investor@example.com").username("Investor").build();
        when(authenticatedUserService.getCurrentUser(authentication)).thenReturn(user);
    }

    @Test
    void createsAValidInvestmentForTheAuthenticatedUser() {
        stubSaveWithId(10L);

        InvestmentResponse response = investmentService.createInvestment(
                authentication, request("AAPL", "10", "180.00", LocalDate.of(2026, 1, 10)));

        ArgumentCaptor<Investment> captor = ArgumentCaptor.forClass(Investment.class);
        verify(investmentRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    void retrievesOnlyTheAuthenticatedUsersInvestments() {
        when(investmentRepository.findByUserIdOrderByPurchaseDateDesc(25L))
                .thenReturn(List.of(investment(1L, user, "AAPL", "10", "180", "2026-01-10")));

        List<InvestmentResponse> responses = investmentService.getInvestments(authentication, null);

        assertThat(responses).extracting(InvestmentResponse::getTicker).containsExactly("AAPL");
        verify(investmentRepository).findByUserIdOrderByPurchaseDateDesc(25L);
    }

    @Test
    void updatesAnOwnedPurchaseLot() {
        Investment existing = investment(1L, user, "AAPL", "10", "180", "2026-01-10");
        when(investmentRepository.findByIdAndUserId(1L, 25L)).thenReturn(Optional.of(existing));
        when(investmentRepository.save(existing)).thenReturn(existing);

        InvestmentResponse response = investmentService.updateInvestment(
                authentication, 1L, request("MSFT", "2.5", "400", LocalDate.of(2026, 2, 2)));

        assertThat(response.getTicker()).isEqualTo("MSFT");
        assertThat(response.getShares()).isEqualByComparingTo("2.5");
        assertThat(response.getPurchasePrice()).isEqualByComparingTo("400");
    }

    @Test
    void deletesAnOwnedPurchaseLot() {
        Investment existing = investment(1L, user, "AAPL", "10", "180", "2026-01-10");
        when(investmentRepository.findByIdAndUserId(1L, 25L)).thenReturn(Optional.of(existing));

        investmentService.deleteInvestment(authentication, 1L);

        verify(investmentRepository).delete(existing);
    }

    @Test
    void userCannotRetrieveAnotherUsersPurchases() {
        List<InvestmentResponse> responses = investmentService.getInvestments(authentication, null);

        assertThat(responses).isEmpty();
        verify(investmentRepository).findByUserIdOrderByPurchaseDateDesc(25L);
    }

    @Test
    void userCannotEditAnotherUsersPurchase() {
        when(investmentRepository.findByIdAndUserId(99L, 25L)).thenReturn(Optional.empty());

        assertNotFound(() -> investmentService.updateInvestment(
                authentication, 99L, request("AAPL", "1", "100", LocalDate.of(2026, 1, 1))));

        verify(investmentRepository, never()).save(any(Investment.class));
    }

    @Test
    void userCannotDeleteAnotherUsersPurchase() {
        when(investmentRepository.findByIdAndUserId(99L, 25L)).thenReturn(Optional.empty());

        assertNotFound(() -> investmentService.deleteInvestment(authentication, 99L));

        verify(investmentRepository, never()).delete(any(Investment.class));
    }

    @Test
    void rejectsZeroShares() {
        assertBadRequest(
                () -> investmentService.createInvestment(
                        authentication, request("AAPL", "0", "100", LocalDate.of(2026, 1, 1))),
                "Shares must be greater than 0");
    }

    @Test
    void rejectsNegativeShares() {
        assertBadRequest(
                () -> investmentService.createInvestment(
                        authentication, request("AAPL", "-1", "100", LocalDate.of(2026, 1, 1))),
                "Shares must be greater than 0");
    }

    @Test
    void rejectsZeroPurchasePrice() {
        assertBadRequest(
                () -> investmentService.createInvestment(
                        authentication, request("AAPL", "1", "0", LocalDate.of(2026, 1, 1))),
                "Purchase price must be greater than 0");
    }

    @Test
    void rejectsNegativePurchasePrice() {
        assertBadRequest(
                () -> investmentService.createInvestment(
                        authentication, request("AAPL", "1", "-100", LocalDate.of(2026, 1, 1))),
                "Purchase price must be greater than 0");
    }

    @Test
    void rejectsBlankTicker() {
        assertBadRequest(
                () -> investmentService.createInvestment(
                        authentication, request("   ", "1", "100", LocalDate.of(2026, 1, 1))),
                "Ticker cannot be empty");
    }

    @Test
    void normalizesTickerToUppercase() {
        stubSaveWithId(1L);

        InvestmentResponse response = investmentService.createInvestment(
                authentication, request("  brk.b  ", "1", "500", LocalDate.of(2026, 1, 1)));

        assertThat(response.getTicker()).isEqualTo("BRK.B");
    }

    @Test
    void calculatesAmountInvestedForAPurchase() {
        stubSaveWithId(1L);

        InvestmentResponse response = investmentService.createInvestment(
                authentication, request("VOO", "1.5", "180.50", LocalDate.of(2026, 1, 1)));

        assertThat(response.getAmountInvested()).isEqualByComparingTo("270.750");
    }

    @Test
    void keepsMultiplePurchasesOfTheSameTickerSeparate() {
        when(investmentRepository.findByUserIdOrderByPurchaseDateDesc(25L)).thenReturn(List.of(
                investment(1L, user, "AAPL", "10", "180", "2026-01-10"),
                investment(2L, user, "AAPL", "5", "195", "2026-03-04")
        ));

        List<InvestmentResponse> responses = investmentService.getInvestments(authentication, null);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(InvestmentResponse::getId).containsExactly(1L, 2L);
    }

    @Test
    void aggregatesTotalSharesByTicker() {
        stubAaplPurchases();

        InvestmentHoldingResponse holding = investmentService.getHoldings(authentication).getFirst();

        assertThat(holding.getTotalShares()).isEqualByComparingTo("15");
    }

    @Test
    void aggregatesTotalInvestedByTicker() {
        stubAaplPurchases();

        InvestmentHoldingResponse holding = investmentService.getHoldings(authentication).getFirst();

        assertThat(holding.getTotalInvested()).isEqualByComparingTo("2775");
    }

    @Test
    void calculatesWeightedAveragePurchasePrice() {
        stubAaplPurchases();

        InvestmentHoldingResponse holding = investmentService.getHoldings(authentication).getFirst();

        assertThat(holding.getAveragePurchasePrice()).isEqualByComparingTo("185.0000");
        assertThat(holding.getPurchaseCount()).isEqualTo(2);
    }

    @Test
    void preservesFractionalSharePrecision() {
        when(investmentRepository.findByUserIdOrderByPurchaseDateDesc(25L)).thenReturn(List.of(
                investment(1L, user, "VOO", "0.125", "400.12", "2026-01-10"),
                investment(2L, user, "VOO", "1.375", "420.08", "2026-03-04")
        ));

        InvestmentHoldingResponse holding = investmentService.getHoldings(authentication).getFirst();

        assertThat(holding.getTotalShares()).isEqualByComparingTo("1.500");
        assertThat(holding.getTotalInvested()).isEqualByComparingTo("627.62500");
        assertThat(holding.getAveragePurchasePrice()).isEqualByComparingTo("418.4167");
    }

    @Test
    void deletingOnePurchaseRecalculatesHoldingTotals() {
        Investment first = investment(1L, user, "AAPL", "10", "180", "2026-01-10");
        Investment second = investment(2L, user, "AAPL", "5", "195", "2026-03-04");
        when(investmentRepository.findByUserIdOrderByPurchaseDateDesc(25L))
                .thenReturn(List.of(first, second), List.of(second));
        when(investmentRepository.findByIdAndUserId(1L, 25L)).thenReturn(Optional.of(first));

        InvestmentHoldingResponse before = investmentService.getHoldings(authentication).getFirst();
        investmentService.deleteInvestment(authentication, 1L);
        InvestmentHoldingResponse after = investmentService.getHoldings(authentication).getFirst();

        assertThat(before.getTotalInvested()).isEqualByComparingTo("2775");
        assertThat(after.getTotalShares()).isEqualByComparingTo("5");
        assertThat(after.getTotalInvested()).isEqualByComparingTo("975");
        assertThat(after.getAveragePurchasePrice()).isEqualByComparingTo("195.0000");
    }

    @Test
    void rejectsFuturePurchaseDates() {
        assertBadRequest(
                () -> investmentService.createInvestment(
                        authentication, request("AAPL", "1", "100", LocalDate.now().plusDays(1))),
                "Purchase date cannot be in the future");
    }

    private void stubAaplPurchases() {
        when(investmentRepository.findByUserIdOrderByPurchaseDateDesc(25L)).thenReturn(List.of(
                investment(1L, user, "AAPL", "10", "180", "2026-01-10"),
                investment(2L, user, "AAPL", "5", "195", "2026-03-04")
        ));
    }

    private void stubSaveWithId(Long id) {
        when(investmentRepository.save(any(Investment.class))).thenAnswer(invocation -> {
            Investment investment = invocation.getArgument(0);
            investment.setId(id);
            return investment;
        });
    }

    private InvestmentRequest request(
            String ticker,
            String shares,
            String purchasePrice,
            LocalDate purchaseDate
    ) {
        return InvestmentRequest.builder()
                .ticker(ticker)
                .shares(new BigDecimal(shares))
                .purchasePrice(new BigDecimal(purchasePrice))
                .purchaseDate(purchaseDate)
                .build();
    }

    private Investment investment(
            Long id,
            User owner,
            String ticker,
            String shares,
            String purchasePrice,
            String purchaseDate
    ) {
        return Investment.builder()
                .id(id)
                .user(owner)
                .ticker(ticker)
                .shares(new BigDecimal(shares))
                .purchasePrice(new BigDecimal(purchasePrice))
                .purchaseDate(LocalDate.parse(purchaseDate))
                .build();
    }

    private void assertBadRequest(Runnable action, String reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo(reason);
                });
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Investment not found");
                });
    }
}
