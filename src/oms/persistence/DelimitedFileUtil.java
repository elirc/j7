package oms.persistence;

import java.util.ArrayList;
import java.util.List;

public final class DelimitedFileUtil {
    private DelimitedFileUtil() {
    }

    public static String join(String... fields) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                builder.append('|');
            }
            builder.append(escape(fields[i]));
        }
        return builder.toString();
    }

    public static List<String> split(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaping = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (escaping) {
                if (ch == 'n') {
                    current.append('\n');
                } else if (ch == 'r') {
                    current.append('\r');
                } else if (ch == '|' || ch == '\\') {
                    current.append(ch);
                } else {
                    throw new IllegalArgumentException("Unknown escape sequence.");
                }
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else if (ch == '|') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        if (escaping) throw new IllegalArgumentException("Incomplete escape sequence.");
        parts.add(current.toString());
        return parts;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
