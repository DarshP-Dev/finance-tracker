package com.financetracker.backend.services.market;

import static org.assertj.core.api.Assertions.assertThat;
import static com.financetracker.backend.services.market.MarketDataProvider.Status.*;
import com.financetracker.backend.config.MarketDataProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class TwelveDataMarketDataProviderTests {
    private HttpServer server;
    private final MarketDataProperties properties = new MarketDataProperties();
    private TwelveDataMarketDataProvider provider;
    private String response;
    private int httpStatus = 200;
    private String requestQuery, authHeader;
    private long delay;
    @BeforeEach void setup() throws Exception {
        response = quote("AAPL", "207.30000");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestQuery = exchange.getRequestURI().getRawQuery();
            authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            try { if (delay > 0) Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(httpStatus, bytes.length); exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        });
        server.start();
        properties.setApiKey("test-only-placeholder");
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/quote");
        properties.setEodEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/eod");
        provider = new TwelveDataMarketDataProvider(properties, new ObjectMapper());
    }
    @AfterEach void cleanup() { server.stop(0); }
    @Test void mapsActualQuoteShapeIncludingOptionalTimestampAndPreviousClose() {
        var quote = provider.getQuotes(Set.of("AAPL")).get("AAPL").quote();
        assertThat(quote.currentPrice()).isEqualByComparingTo("207.30000");
        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.previousClose()).isEqualByComparingTo("200");
        assertThat(quote.marketTimestamp().getEpochSecond()).isEqualTo(1791379800L);
    }
    @Test void apiKeyIsInHeaderAndNotUrl() {
        provider.getQuotes(Set.of("AAPL"));
        assertThat(authHeader).isEqualTo("apikey test-only-placeholder");
        assertThat(requestQuery).doesNotContain("apikey", "placeholder");
    }
    @Test void batchMapsEachTickerAndIndividualFailure() {
        response = "{\"AAPL\":" + quote("AAPL", "207.3") + ",\"MISSING\":{\"status\":\"error\",\"code\":400}}";
        var result = provider.getQuotes(Set.of("AAPL", "MISSING"));
        assertThat(result.get("AAPL").status()).isEqualTo(AVAILABLE);
        assertThat(result.get("MISSING").status()).isEqualTo(UNKNOWN_SYMBOL);
        assertThat(requestQuery).contains("AAPL%2CMISSING");
    }
    @Test void malformedJsonAndZeroOrNegativePricesAreUnavailable() {
        for (String body : new String[]{"invalid json", quote("AAPL", "0"), quote("AAPL", "-10"), quote("AAPL", "NaN")}) {
            response = body;
            assertThat(provider.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isNull();
        }
    }
    @Test void currencyMismatchNeverGetsUsedAsUsd() {
        response = quote("AAPL", "207.3").replace("USD", "CAD");
        assertThat(provider.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(UNSUPPORTED_CURRENCY);
    }
    @Test void unsupportedAssetTypeIsNotValued() {
        response = quote("AAPL", "207.3").replace("\"currency\":\"USD\"", "\"currency\":\"USD\",\"type\":\"Digital Currency\"");
        assertThat(provider.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(UNSUPPORTED_ASSET);
    }
    @Test void wrongSymbolAndMissingFieldsCannotCreateFakePrice() {
        response = quote("MSFT", "207.3");
        assertThat(provider.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isNull();
    }
    @Test void rateLimitAndProviderErrorAreGraceful() {
        httpStatus = 429;
        assertThat(provider.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(RATE_LIMITED);
        httpStatus = 500;
        assertThat(provider.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(UNAVAILABLE);
    }
    @Test void fullBodyTimeoutIsBoundedAndReturnsUnavailable() {
        properties.setTimeout(Duration.ofMillis(500)); delay = 1000;
        assertThat(provider.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(UNAVAILABLE);
    }
    @Test void missingKeyAndUnsafeEndpointAreUnconfigured() {
        properties.setApiKey(""); assertThat(provider.isConfigured()).isFalse();
        properties.setApiKey("placeholder"); properties.setEndpoint("http://example.com/quote");
        assertThat(provider.isConfigured()).isFalse();
    }
    @Test void invalidSymbolDoesNotGetSent() {
        assertThat(provider.getQuotes(Set.of("AAPL&apikey=bad")).values()).allSatisfy(r -> assertThat(r.status()).isEqualTo(INVALID_SYMBOL));
        assertThat(requestQuery).isNull();
    }
    @Test void datedEodResponseConfirmsRegularSessionWithoutInventingMarketTimestamp() {
        response = eod("AAPL", "2026-10-09");
        var result = provider.getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 9)).get("AAPL");
        assertThat(result.status()).isEqualTo(AVAILABLE);
        assertThat(result.quote().confirmedClose()).isTrue();
        assertThat(result.quote().sessionDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(result.quote().marketTimestamp()).isNull();
        assertThat(requestQuery).contains("date=2026-10-09", "prepost=false").doesNotContain("apikey", "placeholder");
        assertThat(authHeader).isEqualTo("apikey test-only-placeholder");
    }
    @Test void olderEodResponseRetainsActualSessionAndIsStale() {
        response = eod("AAPL", "2026-10-08");
        var result = provider.getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 9)).get("AAPL");
        assertThat(result.status()).isEqualTo(STALE);
        assertThat(result.quote().sessionDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    }
    @Test void futureInvalidOrMissingEodDateCannotConfirmClosingPrice() {
        for (String date : new String[]{"2026-10-10", "", "not-a-date"}) {
            response = eod("AAPL", date);
            assertThat(provider.getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 9)).get("AAPL").quote()).isNull();
        }
        response = quote("AAPL", "200");
        assertThat(provider.getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 9)).get("AAPL").quote()).isNull();
    }
    @Test void ordinaryQuotesNeverConfirmClosingPrice() {
        assertThat(provider.getQuotes(Set.of("AAPL")).get("AAPL").quote().confirmedClose()).isFalse();
    }
    @Test void eodBatchKeepsPerSymbolFailuresAndUsesOneRequest() {
        response = "{\"AAPL\":" + eod("AAPL", "2026-10-09") + ",\"MSFT\":{\"status\":\"error\",\"code\":429}}";
        var results = provider.getClosingQuotes(Set.of("MSFT", "AAPL"), LocalDate.of(2026, 10, 9));
        assertThat(results.get("AAPL").quote().confirmedClose()).isTrue();
        assertThat(results.get("MSFT").status()).isEqualTo(RATE_LIMITED);
        assertThat(requestQuery).contains("symbol=AAPL%2CMSFT");
    }
    @Test void eodEntitlementFailureIsUnavailableAndUnsafeEndpointNeverReceivesKey() {
        httpStatus = 403;
        assertThat(provider.getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 9)).get("AAPL").status()).isEqualTo(UNAVAILABLE);
        requestQuery = null;
        properties.setEodEndpoint("http://example.com/eod");
        assertThat(provider.getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 9)).get("AAPL").quote()).isNull();
        assertThat(requestQuery).isNull();
    }
    @Test void diagnosticsReportHttpAndProviderErrorWithoutLeakingKeyOrResponseBody(CapturedOutput output) {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(TwelveDataMarketDataProvider.class);
        var previous = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            response = "{\"status\":\"error\",\"code\":403,\"message\":\"private-provider-body-test-only-placeholder\"}";
            provider.getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 9));
            assertThat(output.getAll()).contains("mode=EOD", "eodSession=2026-10-09", "httpStatus=200", "providerErrorCode=403", "status=UNAVAILABLE")
                    .doesNotContain("test-only-placeholder", "private-provider-body", "Authorization", "apikey ");
        } finally { logger.setLevel(previous); }
    }
    private String eod(String symbol, String date) {
        return "{\"symbol\":\"" + symbol + "\",\"currency\":\"USD\",\"mic_code\":\"XNGS\",\"close\":\"207.3\",\"datetime\":\"" + date + "\"}";
    }
    private String quote(String symbol, String price) {
        return "{\"symbol\":\"" + symbol + "\",\"currency\":\"USD\",\"mic_code\":\"XNGS\",\"close\":\"" + price + "\",\"previous_close\":\"200\",\"timestamp\":1791379800}";
    }
}
