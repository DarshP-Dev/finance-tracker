package com.financetracker.backend.migration;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlywayApplicationIntegrationTests {
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    @LocalServerPort int port;
    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test void migratedSchemaPassesHibernateValidationAndRestartChecks() throws Exception {
        // Reaching this running context proves Hibernate validate ran after Flyway.
        try (var connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            AuthenticationMigrationTests.verifyV1Preserved(connection);
            connection.rollback();
        }
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='2' and type='SQL' and success",Integer.class)).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='1' and success",Integer.class)).isEqualTo(1);
    }

    @Test void registrationJwtCrudAndFinancialQueriesWorkOnMigratedSchema() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","");
        String email="flyway_"+suffix+"@example.invalid";
        var registration=call("/api/auth/register","POST","""
                {"username":"flyway_%s","email":"%s","password":"test-password-only"}
                """.formatted(suffix,email),null);
        assertThat(registration.statusCode()).isEqualTo(201);
        var registered=mapper.readTree(registration.body());
        String token=registered.path("accessToken").asText();
        long owner=registered.path("userId").asLong();
        try {
            assertThat(call("/api/auth/login","POST","{\"email\":\""+email+"\",\"password\":\"test-password-only\"}",null).statusCode()).isEqualTo(200);
            String transaction="""
                {"amount":25.1234,"category":"DINING","type":"EXPENSE","description":"Migration test","date":"%s","merchant":"Test"}
                """.formatted(LocalDate.now());
            var created=call("/api/transactions","POST",transaction,token);
            assertThat(created.statusCode()).isEqualTo(201);
            long id=mapper.readTree(created.body()).path("id").asLong();
            assertThat(call("/api/transactions/"+id,"PUT",transaction.replace("25.1234","35.4321"),token).statusCode()).isEqualTo(200);
            String budget="{\"category\":\"DINING\",\"monthlyLimit\":100,\"month\":\""+YearMonth.now()+"\"}";
            var budgetResponse=call("/api/budgets","POST",budget,token);
            assertThat(budgetResponse.statusCode()).isEqualTo(201);
            long budgetId=mapper.readTree(budgetResponse.body()).path("id").asLong();
            assertThat(call("/api/budgets/"+budgetId,"PUT",budget.replace(":100",":120"),token).statusCode()).isEqualTo(200);
            assertThat(call("/api/recurring-transactions","POST","""
                {"amount":10,"category":"OTHER","type":"EXPENSE","description":"Migration recurring","frequency":"MONTHLY","startDate":"%s"}
                """.formatted(LocalDate.now()),token).statusCode()).isEqualTo(201);
            assertThat(call("/api/investments","POST","""
                {"ticker":"TEST","shares":1.123456,"purchasePrice":50.1234,"purchaseDate":"%s"}
                """.formatted(LocalDate.now()),token).statusCode()).isEqualTo(201);
            for (String endpoint:new String[]{"/api/dashboard","/api/analytics","/api/investments/portfolio?quotePolicy=CACHE_ONLY",
                    "/api/notifications","/api/financial-insights","/api/recurring-transactions"})
                assertThat(call(endpoint,"GET",null,token).statusCode()).as(endpoint).isEqualTo(200);
            assertThat(call("/api/transactions/"+id,"DELETE",null,token).statusCode()).isEqualTo(204);
        } finally {
            // Only this test-owned user, in the guarded disposable cluster.
            for (String table:new String[]{"ai_insights","transactions","budgets","investments","recurring_transactions"})
                jdbc.update("delete from "+table+" where user_id=?",owner);
            jdbc.update("delete from users where id=?",owner);
        }
    }

    private HttpResponse<String> call(String path,String method,String body,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if (path.startsWith("/api/auth/") && method.equals("POST")) {
            var csrf = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            var value = mapper.readTree(csrf.body());
            request.header(value.path("headerName").asText(), value.path("token").asText());
        }
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(body!=null) request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
}
