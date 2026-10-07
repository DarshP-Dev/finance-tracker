package com.financetracker.backend.services.ai;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Defense in depth, not a proof of semantic accuracy. The source cards remain canonical. */
@Component
public class InsightSummaryValidator {
    public enum RejectionReason { NONE, EMPTY, TOO_LONG, CONTROL_CHARACTERS, MARKUP, UNSUPPORTED_LANGUAGE, UNKNOWN_NUMBER_OR_UNIT, UNKNOWN_SPELLED_NUMBER }
    public enum UnsupportedRule { NONE, ADVICE_OR_PRODUCT_LANGUAGE, MARKET_VALUE, PORTFOLIO_PERFORMANCE, UNREALIZED_RESULTS, LINK, INSTRUCTION_OVERRIDE }
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])[-+−]?\\$?\\d+(?:,\\d{3})*(?:\\.\\d+)?%?");
    private static final Pattern UNSUPPORTED = Pattern.compile(
            "(?i)\\b(you should|recommend\\w*|buy|sell|borrow|loan|credit product|tax advice|legal advice|invest in|" +
            "portfolio performance|market value|unrealized|investment returns?|portfolio returns?|realized (?:gains?|losses?|profit)|https?|ignore (?:previous|prior) instructions)\\b");
    private static final Pattern PURCHASE_COST_LIMITATION = Pattern.compile(
            "(?i)\\b(?:not (?:a measure of )?|rather than |(?:does|do) not (?:reflect|represent|indicate|measure) (?:the )?|" +
            "without (?:implying|representing) )(?:current |live )?market value\\b");
    private static final Pattern SPELLED_NUMBER = Pattern.compile(
            "(?i)\\b(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|" +
            "sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion)\\b");

    public boolean isValid(String summary, List<InsightSummaryClient.SourceInsight> source, int maxLength) {
        return rejectionReason(summary, source, maxLength) == RejectionReason.NONE;
    }

    public RejectionReason rejectionReason(String summary, List<InsightSummaryClient.SourceInsight> source, int maxLength) {
        if (summary == null || summary.isBlank()) return RejectionReason.EMPTY;
        if (summary.length() > maxLength) return RejectionReason.TOO_LONG;
        if (summary.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r')) return RejectionReason.CONTROL_CHARACTERS;
        if (summary.contains("<") || summary.contains(">")) return RejectionReason.MARKUP;
        if (unsupportedRule(summary, source) != UnsupportedRule.NONE) return RejectionReason.UNSUPPORTED_LANGUAGE;
        Set<String> allowed = new HashSet<>();
        String sourceText = source.stream().map(InsightSummaryClient.SourceInsight::message)
                .reduce("", (a, b) -> a + " " + b);
        NUMBER.matcher(sourceText).results().forEach(m -> allowed.add(numberIdentity(m.group())));
        if (NUMBER.matcher(summary).results().anyMatch(m -> !allowed.contains(numberIdentity(m.group())))) return RejectionReason.UNKNOWN_NUMBER_OR_UNIT;
        String lowerSource = sourceText.toLowerCase(Locale.ROOT);
        if (SPELLED_NUMBER.matcher(summary).results().anyMatch(m -> !Pattern.compile("\\b" + m.group().toLowerCase(Locale.ROOT) + "\\b").matcher(lowerSource).find())) return RejectionReason.UNKNOWN_SPELLED_NUMBER;
        return RejectionReason.NONE;
    }

    /** Recognized negative valuation caveats are permitted only for recorded purchase-cost insights. */
    public UnsupportedRule unsupportedRule(String summary, List<InsightSummaryClient.SourceInsight> source) {
        if (summary == null) return UnsupportedRule.NONE;
        boolean hasRecordedPurchases = source.stream().anyMatch(insight ->
                insight.type() == com.financetracker.backend.dto.FinancialInsightResponse.Type.INVESTMENT
                        && insight.message().toLowerCase(Locale.ROOT).contains("purchase cost"));
        String checkedText = hasRecordedPurchases ? PURCHASE_COST_LIMITATION.matcher(summary).replaceAll(" ") : summary;
        boolean hasValuation = source.stream().anyMatch(i ->
                i.type() == com.financetracker.backend.dto.FinancialInsightResponse.Type.INVESTMENT
                        && "Tracked portfolio".equals(i.title()) && i.message().contains("market value")
                        && i.message().contains("unrealized"));
        if (hasValuation) checkedText = checkedText.replaceAll("(?i)\\b(?:market value|portfolio performance|unrealized|investment returns?|portfolio returns?)\\b", " ");
        var match = UNSUPPORTED.matcher(checkedText);
        if (!match.find()) return UnsupportedRule.NONE;
        return switch (match.group().toLowerCase(Locale.ROOT)) {
            case "market value" -> UnsupportedRule.MARKET_VALUE;
            case "portfolio performance" -> UnsupportedRule.PORTFOLIO_PERFORMANCE;
            case "unrealized" -> UnsupportedRule.UNREALIZED_RESULTS;
            case "http", "https" -> UnsupportedRule.LINK;
            case "ignore previous instructions", "ignore prior instructions" -> UnsupportedRule.INSTRUCTION_OVERRIDE;
            default -> UnsupportedRule.ADVICE_OR_PRODUCT_LANGUAGE;
        };
    }

    private String numberIdentity(String text) {
        String unit = text.contains("$") ? "money" : text.endsWith("%") ? "percent" : "number";
        String numeric = text.replace(",", "").replace("$", "").replace("%", "").replace('−', '-');
        return unit + ":" + new BigDecimal(numeric).stripTrailingZeros().toPlainString();
    }
}
