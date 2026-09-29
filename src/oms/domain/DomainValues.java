package oms.domain;
import java.math.BigDecimal;
import java.math.RoundingMode;
public final class DomainValues {
    private DomainValues() { }
    public static String text(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }
    public static BigDecimal money(BigDecimal value, boolean allowZero) {
        if (value == null) throw new IllegalArgumentException("Money value is required.");
        BigDecimal normalized;
        try { normalized = value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("Money must use at most 2 decimal places.", error); }
        if (normalized.signum() < 0 || (!allowZero && normalized.signum() == 0))
            throw new IllegalArgumentException(allowZero ? "Money cannot be negative." : "Money must be greater than zero.");
        return normalized;
    }
}
