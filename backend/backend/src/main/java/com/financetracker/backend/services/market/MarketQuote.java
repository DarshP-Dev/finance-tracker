package com.financetracker.backend.services.market;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(String symbol, BigDecimal currentPrice, String currency,
                          BigDecimal previousClose, Instant marketTimestamp) {}
