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
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])[-+−]?\\$?\\d+(?:,\\d{3})*(?:\\.\\d+)?%?");
    private static final Pattern UNSUPPORTED = Pattern.compile(
            "(?i)\\b(you should|recommend\\w*|buy|sell|borrow|loan|credit product|tax advice|legal advice|invest in|" +
            "portfolio performance|(?<!not )(?<!not current )market value|unrealized|https?|ignore (?:previous|prior) instructions)\\b");
    private static final Pattern SPELLED_NUMBER = Pattern.compile(
            "(?i)\\b(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|" +
            "sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion)\\b");

    public boolean isValid(String summary, List<InsightSummaryClient.SourceInsight> source, int maxLength) {
        if (summary == null || summary.isBlank() || summary.length() > maxLength
                || summary.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r')
                || summary.contains("<") || summary.contains(">") || UNSUPPORTED.matcher(summary).find()) return false;
        Set<String> allowed = new HashSet<>();
        String sourceText = source.stream().map(InsightSummaryClient.SourceInsight::message)
                .reduce("", (a, b) -> a + " " + b);
        NUMBER.matcher(sourceText).results().forEach(m -> allowed.add(numberIdentity(m.group())));
        if (NUMBER.matcher(summary).results().anyMatch(m -> !allowed.contains(numberIdentity(m.group())))) return false;
        String lowerSource = sourceText.toLowerCase(Locale.ROOT);
        if (SPELLED_NUMBER.matcher(summary).results().anyMatch(m -> !Pattern.compile("\\b" + m.group().toLowerCase(Locale.ROOT) + "\\b").matcher(lowerSource).find())) return false;
        return true;
    }

    private String numberIdentity(String text) {
        String unit = text.contains("$") ? "money" : text.endsWith("%") ? "percent" : "number";
        String numeric = text.replace(",", "").replace("$", "").replace("%", "").replace('−', '-');
        return unit + ":" + new BigDecimal(numeric).stripTrailingZeros().toPlainString();
    }
}
