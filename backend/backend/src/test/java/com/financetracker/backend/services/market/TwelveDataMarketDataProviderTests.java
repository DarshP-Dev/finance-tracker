package com.financetracker.backend.services.market;

import static org.assertj.core.api.Assertions.assertThat;
import static com.financetracker.backend.services.market.MarketDataProvider.Status.*;
import com.financetracker.backend.config.MarketDataProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

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
        server.createContext("/quote", exchange -> {
            requestQuery = exchange.getRequestURI().getRawQuery();
            authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            try { if (delay > 0) Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(httpStatus, bytes.length); exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        });
        server.start();
        properties.setApiKey("test-only-placeholder");
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/quote");
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
    private String quote(String symbol, String price) {
        return "{\"symbol\":\"" + symbol + "\",\"currency\":\"USD\",\"mic_code\":\"XNGS\",\"close\":\"" + price + "\",\"previous_close\":\"200\",\"timestamp\":1791379800}";
    }
}
