package com.financetracker.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.backend.controllers.AuthController;
import com.financetracker.backend.controllers.AnalyticsController;
import com.financetracker.backend.controllers.DashboardController;
import com.financetracker.backend.controllers.RecurringTransactionController;
import com.financetracker.backend.controllers.RecurringCronController;
import com.financetracker.backend.services.AnalyticsService;
import com.financetracker.backend.services.AuthService;
import com.financetracker.backend.services.DashboardService;
import com.financetracker.backend.services.RecurringTransactionService;
import com.financetracker.backend.services.RecurringTransactionProcessor;
import org.junit.jupiter.api.Test;
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
    private RecurringTransactionService recurringTransactionService;

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
