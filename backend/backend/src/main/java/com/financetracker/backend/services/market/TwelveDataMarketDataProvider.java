package com.financetracker.backend.services.market;

import com.financetracker.backend.config.MarketDataProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Maps provider data to application quotes. Never logs credentials, URLs, or provider bodies. */
@Component
public class TwelveDataMarketDataProvider implements MarketDataProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(TwelveDataMarketDataProvider.class);
    private final MarketDataProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public TwelveDataMarketDataProvider(MarketDataProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }
    @Override public boolean isConfigured() {
        try {
            URI endpoint = URI.create(properties.getEndpoint());
            boolean loopback = "http".equals(endpoint.getScheme())
                    && List.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost());
            return "twelve-data".equals(properties.getProvider()) && properties.getApiKey() != null
                    && !properties.getApiKey().isBlank() && endpoint.getHost() != null
                    && ("https".equals(endpoint.getScheme()) || loopback)
                    && endpoint.getUserInfo() == null && endpoint.getQuery() == null && endpoint.getFragment() == null;
        } catch (RuntimeException exception) { return false; }
    }
    @Override public Map<String, Result> getQuotes(Set<String> symbols) {
        return fetch(symbols, null);
    }
    @Override public Map<String, Result> getClosingQuotes(Set<String> symbols, LocalDate sessionDate) {
        if (sessionDate == null || !safeEndpoint(properties.getEodEndpoint())) {
            LOGGER.debug("Market provider EOD request skipped: reason=INVALID_ENDPOINT_OR_SESSION");
            var unavailable = new LinkedHashMap<String, Result>();
            symbols.forEach(s -> unavailable.put(s, Result.unavailable(Status.UNAVAILABLE)));
            return unavailable;
        }
        return fetch(symbols, sessionDate);
    }
    private boolean safeEndpoint(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                    && List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())));
        } catch (RuntimeException exception) { return false; }
    }
    private Map<String, Result> fetch(Set<String> symbols, LocalDate closingSession) {
        Map<String, Result> results = new LinkedHashMap<>();
        if (symbols.isEmpty()) return results;
        if (!isConfigured()) {
            LOGGER.debug("Market provider request skipped: reason=UNCONFIGURED, keyPresent={}",
                    properties.getApiKey() != null && !properties.getApiKey().isBlank());
            symbols.forEach(s -> results.put(s, Result.unavailable(Status.UNAVAILABLE))); return results;
        }
        var valid = symbols.stream().filter(s -> s.matches("[A-Z0-9][A-Z0-9.-]{0,19}")).sorted().toList();
        symbols.stream().filter(s -> !valid.contains(s)).forEach(s -> results.put(s, Result.unavailable(Status.INVALID_SYMBOL)));
        if (valid.isEmpty()) return results;
        long timeoutMillis = Math.clamp(properties.getTimeout().toMillis(), 500, 15000);
        String endpoint = closingSession == null ? properties.getEndpoint() : properties.getEodEndpoint();
        var request = HttpRequest.newBuilder(URI.create(endpoint + "?symbol="
                        + URLEncoder.encode(String.join(",", valid), StandardCharsets.UTF_8)
                        + (closingSession == null ? "" : "&date=" + closingSession + "&prepost=false")))
                .header("Authorization", "apikey " + properties.getApiKey())
                .header("Accept", "application/json").timeout(Duration.ofMillis(timeoutMillis)).GET().build();
        var pending = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        LOGGER.debug("Market provider HTTP attempted: mode={}, eodSession={}, symbols={}", closingSession == null ? "QUOTE" : "EOD", closingSession, valid.size());
        try {
            var response = pending.get(timeoutMillis, TimeUnit.MILLISECONDS);
            LOGGER.debug("Market provider HTTP response: mode={}, eodSession={}, httpStatus={}",
                    closingSession == null ? "QUOTE" : "EOD", closingSession, response.statusCode());
            if (response.statusCode() != 200) {
                Status status = response.statusCode() == 429 ? Status.RATE_LIMITED : Status.UNAVAILABLE;
                valid.forEach(s -> results.put(s, Result.unavailable(status)));
            } else if (response.body().length() > 256_000) {
                valid.forEach(s -> results.put(s, Result.unavailable(Status.UNAVAILABLE)));
            } else {
                JsonNode root = mapper.readTree(response.body());
                for (String symbol : valid) {
                    JsonNode node = valid.size() == 1 && root.has("symbol") ? root : root.path(symbol);
                    if (root.has("status") && "error".equals(root.path("status").asText())) node = root;
                    var result = mapQuote(symbol, node, closingSession);
                    LOGGER.debug("Market provider response mapped: status={}, providerErrorCode={}, returnedSession={}, requestedSession={}",
                            result.status(), "error".equals(node.path("status").asText()) ? node.path("code").asInt() : null,
                            result.quote() == null ? null : result.quote().sessionDate(), closingSession);
                    results.put(symbol, result);
                }
            }
        } catch (Exception exception) {
            LOGGER.debug("Market provider HTTP failed: mode={}, eodSession={}, exceptionType={}",
                    closingSession == null ? "QUOTE" : "EOD", closingSession, exception.getClass().getSimpleName());
            pending.cancel(true);
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            valid.forEach(s -> results.put(s, Result.unavailable(Status.UNAVAILABLE)));
        }
        return results;
    }
    private Result mapQuote(String symbol, JsonNode node, LocalDate closingSession) {
        try {
            if ("error".equals(node.path("status").asText())) {
                int code = node.path("code").asInt();
                return Result.unavailable(code == 429 ? Status.RATE_LIMITED : code == 404 || code == 400 ? Status.UNKNOWN_SYMBOL : Status.UNAVAILABLE);
            }
            if (!symbol.equals(node.path("symbol").asText())) return Result.unavailable(Status.UNKNOWN_SYMBOL);
            String currency = node.path("currency").asText();
            if (!"USD".equals(currency)) return Result.unavailable(Status.UNSUPPORTED_CURRENCY);
            // /quote does not always include instrument type. Require a recognized US equity MIC
            // in that case; crypto/forex quotes do not satisfy this listing restriction.
            String type = node.path("type").asText();
            boolean supportedType = Set.of("Common Stock", "Preferred Stock", "ETF", "REIT", "American Depositary Receipt", "Depositary Receipt").contains(type);
            boolean usListing = Set.of("XNGS", "XNMS", "XNCM", "XNAS", "XNYS", "ARCX", "XASE", "BATS", "BATY", "EDGA", "EDGX", "IEXG", "OTCM").contains(node.path("mic_code").asText());
            if ((!type.isEmpty() && !supportedType) || (type.isEmpty() && !usListing)) return Result.unavailable(Status.UNSUPPORTED_ASSET);
            BigDecimal price = new BigDecimal(node.path("close").asText());
            if (price.signum() <= 0 || price.precision() > 24 || price.scale() > 12) return Result.unavailable(Status.UNAVAILABLE);
            BigDecimal previous = node.hasNonNull("previous_close") ? new BigDecimal(node.path("previous_close").asText()) : null;
            Instant timestamp = node.hasNonNull("timestamp") && node.path("timestamp").asLong() > 0
                    ? Instant.ofEpochSecond(node.path("timestamp").asLong()) : null;
            if (closingSession != null) {
                LocalDate actualSession = LocalDate.parse(node.path("datetime").asText());
                if (actualSession.isAfter(closingSession)) return Result.unavailable(Status.UNAVAILABLE);
                return new Result(new MarketQuote(symbol, price, currency, previous, timestamp, actualSession, true),
                        actualSession.equals(closingSession) ? Status.AVAILABLE : Status.STALE);
            }
            return new Result(new MarketQuote(symbol, price, currency, previous, timestamp), Status.AVAILABLE);
        } catch (RuntimeException exception) { return Result.unavailable(Status.UNAVAILABLE); }
    }
}
