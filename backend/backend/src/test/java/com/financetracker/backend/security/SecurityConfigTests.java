package com.financetracker.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.backend.controllers.AuthController;
import com.financetracker.backend.controllers.DashboardController;
import com.financetracker.backend.services.AuthService;
import com.financetracker.backend.services.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, DashboardController.class})
@Import({SecurityConfig.class, JwtFilter.class})
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
