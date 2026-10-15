package com.financetracker.backend.migration;

import static org.assertj.core.api.Assertions.*;
import static com.financetracker.backend.migration.ReconciliationTestDatabase.*;
import com.financetracker.backend.services.RefreshTokenService;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** Real HTTP, real transactions and locks; only the run-tests.ps1 disposable cluster. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationSessionIntegrationTests {
    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired RefreshTokenService sessions;
    @Autowired org.springframework.context.ApplicationContext context;
    CookieManager cookies;
    HttpClient client;
    String email, username;
    long owner;

    @BeforeEach void isolatedAccount() throws Exception {
        try (var c = dataSource.getConnection()) { DisposableDatabaseGuard.requireDisposable(c.getMetaData().getURL()); }
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies).build();
        username = "auth_" + UUID.randomUUID().toString().replace("-", "");
        email = username + "@example.invalid";
        var response = post("register", "{\"username\":\""+username+"\",\"email\":\""+email+"\",\"password\":\"auth-test-password\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        owner = mapper.readTree(response.body()).path("userId").asLong();
    }
    @AfterEach void removeOnlySyntheticAccount() { jdbc.update("delete from users where id=?", owner); }

    @Test void loginUsesBcryptAndStoresOnlyAnOpaqueTokenHash() throws Exception {
        var response = login();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("expiresIn").asLong()).isEqualTo(900);
        assertThat(jdbc.queryForObject("select password from users where id=?",String.class,owner)).startsWith("$2");
        String token = token(); assertThat(token).matches("[A-Za-z0-9_-]{43}");
        String hash = jdbc.queryForObject("select token_hash from refresh_token_sessions where user_id=?", String.class, owner);
        assertThat(hash).matches("[0-9a-f]{64}").isNotEqualTo(token);
        assertThat(response.body()).doesNotContain(token,hash,"refreshToken");
        assertThat(response.headers().allValues("set-cookie").toString()).contains("HttpOnly", "SameSite=Lax", "Path=/api/auth");
        assertThat(response.headers().firstValue("cache-control")).contains("no-store");
    }
    @Test void rotationReturnsNewTokenAndPreservesAbsoluteExpiryAndOwner() throws Exception {
        login(); String old = token();
        var expires = jdbc.queryForObject("select expires_at::text from refresh_token_sessions where user_id=?",String.class,owner);
        var refreshed = post("refresh", null);
        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(token()).isNotEqualTo(old);
        assertThat(mapper.readTree(refreshed.body()).path("userId").asLong()).isEqualTo(owner);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token_sessions where user_id=? and rotated_at is not null and revoked_at is not null",Integer.class,owner)).isEqualTo(1);
        assertThat(jdbc.queryForList("select expires_at::text from refresh_token_sessions where user_id=?",String.class,owner)).containsOnly(expires);
    }
    @Test void replayRevocationCommitsAndInvalidatesTheRotatedSuccessor() throws Exception {
        login(); String old = token(); post("refresh",null); String replacement = token();
        assertThatThrownBy(() -> sessions.rotate(old)).isInstanceOf(RefreshTokenService.InvalidSession.class);
        assertThatThrownBy(() -> sessions.rotate(replacement)).isInstanceOf(RefreshTokenService.InvalidSession.class);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token_sessions where user_id=? and revoked_at is null",Integer.class,owner)).isZero();
    }
    @Test void concurrentRefreshHasOneWinnerThenRevokesTheFamilyOnReuse() throws Exception {
        login(); String original = token();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> rotate = () -> { start.await(); try { sessions.rotate(original); return true; }
                catch (RefreshTokenService.InvalidSession e) { return false; } };
            var first=pool.submit(rotate); var second=pool.submit(rotate); start.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS), second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("select count(*) from refresh_token_sessions where user_id=?",Integer.class,owner)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token_sessions where user_id=? and revoked_at is null",Integer.class,owner)).isZero();
    }
    @Test void expiredRefreshReturns401AndClearsTheCookie() throws Exception {
        login();
        jdbc.update("update refresh_token_sessions set created_at=now()-interval '2 days', expires_at=now()-interval '1 day' where user_id=?",owner);
        var response=post("refresh",null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().allValues("set-cookie").toString()).contains("Max-Age=0");
        assertThat(jdbc.queryForObject("select count(*) from refresh_token_sessions where user_id=?",Integer.class,owner)).isEqualTo(1);
    }
    @Test void logoutIsIdempotentRevokesCookieAndAccessJwtRemainsValidUntilExpiry() throws Exception {
        String access=mapper.readTree(login().body()).path("accessToken").asText(); String refresh=token();
        assertThat(post("logout",null).statusCode()).isEqualTo(204);
        assertThat(post("logout",null).statusCode()).isEqualTo(204);
        assertThatThrownBy(() -> sessions.rotate(refresh)).isInstanceOf(RefreshTokenService.InvalidSession.class);
        assertThat(post("refresh",null).statusCode()).isEqualTo(401);
        var response=client.send(HttpRequest.newBuilder(url("/api/transactions")).header("Authorization","Bearer "+access).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }
    @Test void logoutWithConsumedTokenRevokesItsCurrentFamily() throws Exception {
        login(); String old=token(); post("refresh",null); String next=token();
        sessions.logout(old); sessions.logout(old);
        assertThatThrownBy(() -> sessions.rotate(next)).isInstanceOf(RefreshTokenService.InvalidSession.class);
    }
    @Test void missingInvalidAndTamperedRefreshTokensNeverCreateSessions() throws Exception {
        assertThat(post("refresh",null).statusCode()).isEqualTo(401);
        for(String invalid:List.of("invalid", "a".repeat(43), "a".repeat(44)))
            assertThatThrownBy(() -> sessions.rotate(invalid)).isInstanceOf(RefreshTokenService.InvalidSession.class);
        login(); String original=token();
        String tampered=(original.charAt(0)=='a'?"b":"a")+original.substring(1);
        assertThatThrownBy(() -> sessions.rotate(tampered)).isInstanceOf(RefreshTokenService.InvalidSession.class);
        assertThat(sessions.rotate(original).auth().getUserId()).isEqualTo(owner);
    }
    @Test void authPostsRequireCsrfEvenWithARefreshCookie() throws Exception {
        login(); String before=token();
        for(String endpoint:List.of("login","register","refresh","logout")) {
            var request=HttpRequest.newBuilder(url("/api/auth/"+endpoint)).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).as(endpoint).isEqualTo(403);
        }
        assertThat(token()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token_sessions where user_id=? and revoked_at is null",Integer.class,owner)).isEqualTo(1);
    }
    @Test void cookieAloneCannotAuthorizeFinancialEndpointsAndCorsRejectsOtherOrigins() throws Exception {
        login();
        var anonymous=client.send(HttpRequest.newBuilder(url("/api/transactions")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.body()).contains("AUTHENTICATION_REQUIRED");
        var foreign=client.send(HttpRequest.newBuilder(url("/api/auth/csrf")).header("Origin","https://untrusted.example.invalid").GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(foreign.statusCode()).isEqualTo(403);
    }
    @Test void refreshIgnoresSubmittedUserIdAndDoesNotChangeAnyFinancialTablesOrSequences() throws Exception {
        login(); String data, counters;
        try(var c=dataSource.getConnection()) { data=fingerprint(c); counters=sequences(c); }
        var response=post("refresh","{\"userId\":999999}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("userId").asLong()).isEqualTo(owner);
        try(var c=dataSource.getConnection()) { assertThat(fingerprint(c)).isEqualTo(data); assertThat(sequences(c)).isEqualTo(counters); }
    }
    @Test void wrongLoginHasGenericErrorAndNoRefreshSession() throws Exception {
        var response=post("login","{\"email\":\""+email+"\",\"password\":\"wrong-password\"}");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("Invalid email or password");
        assertThat(jdbc.queryForObject("select count(*) from refresh_token_sessions where user_id=?",Integer.class,owner)).isZero();
    }
    @Test void revokedFamilyDoesNotAffectAnIndependentLogin() throws Exception {
        login(); String first=token(); login(); String second=token();
        sessions.logout(first);
        assertThat(sessions.rotate(second).auth().getUserId()).isEqualTo(owner);
    }
    @Test void passwordChangeRevokesAllRefreshFamiliesAndClearsTheCookie() throws Exception {
        login(); String first=token();
        var secondLogin=login(); String second=token();
        String access=mapper.readTree(secondLogin.body()).path("accessToken").asText();
        var request=HttpRequest.newBuilder(url("/api/account/password"))
                .header("Authorization","Bearer "+access).header("Content-Type","application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"currentPassword\":\"auth-test-password\",\"newPassword\":\"changed-test-password\"}")).build();
        var changed=client.send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(changed.statusCode()).isEqualTo(204);
        assertThat(changed.headers().allValues("set-cookie").toString()).contains("Max-Age=0");
        assertThatThrownBy(() -> sessions.rotate(first)).isInstanceOf(RefreshTokenService.InvalidSession.class);
        assertThatThrownBy(() -> sessions.rotate(second)).isInstanceOf(RefreshTokenService.InvalidSession.class);
        assertThat(post("login","{\"email\":\""+email+"\",\"password\":\"changed-test-password\"}").statusCode()).isEqualTo(200);
    }
    @Test void oldJwtCannotAuthenticateAnAccountThatReusesTheOriginalEmail() throws Exception {
        String access=mapper.readTree(login().body()).path("accessToken").asText();
        jdbc.update("update users set email=? where id=?","changed_"+email,owner);
        var registered=post("register","{\"username\":\"second_"+username+"\",\"email\":\""+email+"\",\"password\":\"auth-test-password\"}");
        assertThat(registered.statusCode()).isEqualTo(201);
        long other=mapper.readTree(registered.body()).path("userId").asLong();
        try {
            var request=HttpRequest.newBuilder(url("/api/transactions")).header("Authorization","Bearer "+access).GET().build();
            assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            assertThat(post("refresh",null).statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(post("refresh",null).body()).path("userId").asLong()).isEqualTo(owner);
        } finally { jdbc.update("delete from users where id=?",other); }
    }
    @Test void sessionCanBeRestoredByANewServiceInstanceAfterRestart() throws Exception {
        login(); String existing=token();
        var fresh=new RefreshTokenService(
                context.getBean(com.financetracker.backend.repositories.RefreshTokenSessionRepository.class),
                context.getBean(com.financetracker.backend.repositories.UserRepository.class),
                context.getBean(com.financetracker.backend.security.JwtService.class),
                context.getBean(java.time.Clock.class), java.time.Duration.ofDays(30));
        var transaction=new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var restored=transaction.execute(status -> fresh.rotate(existing));
        assertThat(restored).isNotNull();
        assertThat(restored.auth().getUserId()).isEqualTo(owner);
        assertThat(restored.token()).isNotEqualTo(existing);
    }
    private URI url(String path) { return URI.create("http://127.0.0.1:"+port+path); }
    private HttpResponse<String> login() throws Exception { return post("login","{\"email\":\""+email+"\",\"password\":\"auth-test-password\"}"); }
    private String token() { return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("financeTrackerRefresh")).findFirst().orElseThrow().getValue(); }
    private HttpResponse<String> post(String path, String body) throws Exception {
        var csrf=client.send(HttpRequest.newBuilder(url("/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
        var token=mapper.readTree(csrf.body());
        var request=HttpRequest.newBuilder(url("/api/auth/"+path)).header("Content-Type","application/json")
                .header(token.path("headerName").asText(),token.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(body==null?"{}":body)).build();
        return client.send(request,HttpResponse.BodyHandlers.ofString());
    }
}
