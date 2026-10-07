package com.financetracker.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;

import com.financetracker.backend.controllers.AuthController;
import com.financetracker.backend.controllers.AnalyticsController;
import com.financetracker.backend.controllers.DashboardController;
import com.financetracker.backend.controllers.RecurringTransactionController;
import com.financetracker.backend.controllers.RecurringCronController;
import com.financetracker.backend.services.AnalyticsService;
import com.financetracker.backend.services.AuthService;
import com.financetracker.backend.services.DashboardService;
import com.financetracker.backend.services.RecurringTransactionService;
import com.financetracker.backend.services.RecurringForecastService;
import com.financetracker.backend.services.RecurringTransactionProcessor;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
        AuthController.class, DashboardController.class, AnalyticsController.class,
        RecurringTransactionController.class, RecurringCronController.class
})
@Import({SecurityConfig.class, JwtFilter.class})
@TestPropertySource(properties = "CRON_SECRET=test-cron-secret")
class SecurityConfigTests {

    private static final String REGISTER_REQUEST = """
            {
              "username": "Test User",
              "email": "test@example.com",
              "password": "password123"
            }
            """;

    private static final String LOGIN_REQUEST = """
            {
              "email": "test@example.com",
              "password": "password123"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private com.financetracker.backend.services.InvestmentPortfolioService portfolioService;

    @BeforeEach void portfolioResponses() {
        when(portfolioService.getPortfolio(any())).thenReturn(new com.financetracker.backend.dto.PortfolioResponse(
                new com.financetracker.backend.dto.PortfolioResponse.Summary(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO, null, 0, 0, "USD", com.financetracker.backend.dto.PortfolioResponse.ValuationStatus.EMPTY, null), java.util.List.of()));
        when(dashboardService.getDashboard(any(), nullable(LocalDate.class), nullable(LocalDate.class))).thenReturn(
                com.financetracker.backend.dto.DashboardResponse.builder().summary(com.financetracker.backend.dto.DashboardSummaryResponse.builder().build()).build());
        when(analyticsService.getAnalytics(any(), any(), nullable(LocalDate.class), nullable(LocalDate.class))).thenReturn(
                new com.financetracker.backend.dto.AnalyticsResponse("THIS_MONTH", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7),
                        null, java.util.List.of(), java.util.List.of(), null, null, null, null));
    }

    @MockitoBean
    private RecurringTransactionService recurringTransactionService;

    @MockitoBean
    private RecurringForecastService recurringForecastService;

    @Test
    void upcomingAndForecastRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/recurring-transactions/upcoming"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/recurring-transactions/forecast"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUsersCanReadUpcomingAndForecast() throws Exception {
        mockMvc.perform(get("/api/recurring-transactions/upcoming")
                        .with(user("owner@example.com")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/recurring-transactions/forecast")
                        .with(user("owner@example.com")))
                .andExpect(status().isOk());
    }

    @Test
    void upcomingAndForecastAcceptIsoDateRanges() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);
        mockMvc.perform(get("/api/recurring-transactions/upcoming")
                        .with(user("owner@example.com"))
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-31"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/recurring-transactions/forecast")
                        .with(user("owner@example.com"))
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-31"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(recurringForecastService)
                .getUpcoming(org.mockito.ArgumentMatchers.any(), org.mockito.Mockito.eq(from), org.mockito.Mockito.eq(to));
        org.mockito.Mockito.verify(recurringForecastService)
                .getForecast(org.mockito.ArgumentMatchers.any(), org.mockito.Mockito.eq(from), org.mockito.Mockito.eq(to));
    }

    @MockitoBean
    private RecurringTransactionProcessor recurringTransactionProcessor;

    @Test
    void cronEndpointRejectsMissingOrIncorrectSecret() throws Exception {
        mockMvc.perform(get(RecurringCronController.PATH))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(RecurringCronController.PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-secret"))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(recurringTransactionProcessor);
    }

    @Test
    void cronEndpointAcceptsConfiguredSecret() throws Exception {
        org.mockito.Mockito.when(recurringTransactionProcessor.processDueRecurringTransactions())
                .thenReturn(new RecurringTransactionProcessor.ProcessingResult(1, 1, 0));

        mockMvc.perform(get(RecurringCronController.PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-cron-secret"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(recurringTransactionProcessor).processDueRecurringTransactions();
    }

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void allowsRegistrationWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_REQUEST))
                .andExpect(status().isCreated());
    }

    @Test
    void allowsLoginWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_REQUEST))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsDashboardWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsDashboardWithAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(user("test@example.com")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnalyticsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAnalyticsWithAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics").with(user("test@example.com")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsRecurringTransactionsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/recurring-transactions"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidRecurringTransactionRequests() throws Exception {
        mockMvc.perform(post("/api/recurring-transactions")
                        .with(user("test@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 0,
                                  "category": "ENTERTAINMENT",
                                  "type": "EXPENSE",
                                  "description": "",
                                  "frequency": "MONTHLY",
                                  "startDate": "2026-10-12"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allowsPublicAuthThroughTheVercelForwardedOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "https://finance-tracker.vercel.app")
                        .header(HttpHeaders.HOST, "backend.internal")
                        .header("X-Forwarded-Host", "finance-tracker.vercel.app")
                        .header("X-Forwarded-Proto", "https")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_REQUEST))
                .andExpect(status().isCreated());
    }
}
