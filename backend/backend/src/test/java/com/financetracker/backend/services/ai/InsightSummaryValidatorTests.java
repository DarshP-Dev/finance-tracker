package com.financetracker.backend.services.ai;

import static org.assertj.core.api.Assertions.assertThat;
import com.financetracker.backend.dto.FinancialInsightResponse.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class InsightSummaryValidatorTests {
    private final InsightSummaryValidator validator = new InsightSummaryValidator();
    private final List<InsightSummaryClient.SourceInsight> source = List.of(new InsightSummaryClient.SourceInsight(
            Type.FORECAST, Severity.INFO, "Known recurring forecast", "Known recurring cash flow is -$340.00 over the next 30 days. Your savings rate is 27%."));
    @Test void acceptsExistingValuesAndUnits() { assertThat(valid("Known recurring cash flow is -$340 over the next 30 days. Your savings rate is 27%." )).isTrue(); }
    @Test void rejectsSignChange() { assertThat(valid("Known recurring cash flow is $340.00.")).isFalse(); }
    @Test void rejectsInventedValues() { assertThat(valid("Your savings rate is 26%.")).isFalse(); }
    @Test void rejectsUnitChanges() { assertThat(valid("Your cash flow is 340%.")).isFalse(); }
    @Test void rejectsSpelledInventedNumbers() { assertThat(valid("You have forty positions.")).isFalse(); }
    @Test void rejectsMarkupAndLinks() { assertThat(valid("<script>alert()</script>")).isFalse(); assertThat(valid("Visit https://example.com")).isFalse(); }
    @Test void rejectsAdviceAndMarketClaims() { assertThat(valid("Invest in stocks.")).isFalse(); assertThat(valid("Your market value increased.")).isFalse(); }
    @Test void permitsAccuratePurchaseCostLimitation() {
        var recorded = List.of(new InsightSummaryClient.SourceInsight(Type.INVESTMENT, Severity.INFO, "Recorded investments",
                "You track 2 investment positions with $100.00 in recorded purchases. This is purchase cost, not current market value."));
        assertThat(validator.isValid("You track 2 investment positions with $100.00 in recorded purchases. This is purchase cost, not current market value.", recorded, 1000)).isTrue();
    }
    @Test void rejectsEmptyNullAndOversizedOutput() { assertThat(valid(null)).isFalse(); assertThat(valid(" ")).isFalse(); assertThat(valid("a".repeat(1001))).isFalse(); }
    @Test void diagnosticsDifferentiateLengthFromChangedNumbersWithoutReturningFinancialText() {
        assertThat(validator.rejectionReason("a".repeat(1001), source, 1000)).isEqualTo(InsightSummaryValidator.RejectionReason.TOO_LONG);
        assertThat(validator.rejectionReason("Your savings rate is 26%.", source, 1000)).isEqualTo(InsightSummaryValidator.RejectionReason.UNKNOWN_NUMBER_OR_UNIT);
        assertThat(validator.rejectionReason("Your savings total $27.", source, 1000)).isEqualTo(InsightSummaryValidator.RejectionReason.UNKNOWN_NUMBER_OR_UNIT);
    }
    @Test void diagnosticsIdentifyOtherRejectionsAndAcceptValidOutput() {
        assertThat(validator.rejectionReason(null, source, 1000)).isEqualTo(InsightSummaryValidator.RejectionReason.EMPTY);
        assertThat(validator.rejectionReason("You should buy stocks.", source, 1000)).isEqualTo(InsightSummaryValidator.RejectionReason.UNSUPPORTED_LANGUAGE);
        assertThat(validator.rejectionReason("You have forty positions.", source, 1000)).isEqualTo(InsightSummaryValidator.RejectionReason.UNKNOWN_SPELLED_NUMBER);
        assertThat(validator.rejectionReason("<p>Text</p>", source, 1000)).isEqualTo(InsightSummaryValidator.RejectionReason.MARKUP);
        assertThat(validator.rejectionReason("Text\u0000", source, 1000)).isEqualTo(InsightSummaryValidator.RejectionReason.CONTROL_CHARACTERS);
        assertThat(validator.rejectionReason("Your savings rate is 27%.", source, 1000)).isEqualTo(InsightSummaryValidator.RejectionReason.NONE);
    }
    private boolean valid(String text) { return validator.isValid(text, source, 1000); }
}
