package com.suryakiran.taskmanagementtool.util;

import java.util.Objects;

/**
 * Makes an untrusted value safe to write into a log line.
 *
 * <p><strong>What this defends against.</strong> The logging in this application already uses
 * the SLF4J parameterised form ({@code logger.info("Creating task: {}", title)}), so the
 * argument is never parsed as a format string. What that does <em>not</em> prevent is
 * <em>log forging</em> (CWE-117): a caller who puts a newline inside a task title, a label
 * name, a comment or an email address can close the current log line and write a further line
 * of their own choosing. A log reader — human or SIEM — has no way to tell that second line
 * from one the application really emitted, so the audit trail stops being evidence. Related,
 * a five-megabyte title logged verbatim floods the log and can push real entries out of
 * retention.</p>
 *
 * <p><strong>What it does.</strong> Truncates over-long input, then replaces every control
 * character — CR and LF above all, but also tab, NUL, the ANSI escape that would let a value
 * repaint a terminal, and the Unicode line separators {@code U+0085}, {@code U+2028} and
 * {@code U+2029} — with {@value #REPLACEMENT}. The result is always exactly one line.
 * Ordinary text is returned unchanged, so the logs stay as readable as before.</p>
 *
 * <p><strong>Where to apply it.</strong> Only to values a request can influence: titles,
 * names, comment bodies, email addresses, string path variables. Wrapping a generated id,
 * an enum, a count or a boolean adds noise without adding safety.</p>
 */
public final class LogSanitizer {

    /** Longest value written to the log; anything beyond this is cut and marked. */
    public static final int MAX_LENGTH = 256;

    /** Substituted for every control character found in the value. */
    static final String REPLACEMENT = "_";

    /** Logged in place of a {@code null} value, so the line still reads unambiguously. */
    static final String NULL_PLACEHOLDER = "<null>";

    /** Appended to a value that was cut short, so a truncated log line is obvious as such. */
    public static final String TRUNCATION_MARKER = "...[truncated]";

    /**
     * ASCII control characters (which covers CR, LF, tab, NUL and ESC) plus the three
     * Unicode characters that a log viewer may also treat as a line break.
     */
    private static final String CONTROL_CHARACTERS = "[\\p{Cntrl}\\u0085\\u2028\\u2029]";

    private LogSanitizer() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns a single-line, length-capped rendering of {@code value}, safe to pass as an
     * SLF4J argument.
     *
     * @param value the untrusted value; may be {@code null}
     * @return the sanitised text, never {@code null} and never containing a line break
     */
    public static String sanitize(Object value) {
        // Objects.toString rather than an `if (value == null)` of our own: an explicit null
        // branch here is a fact the analyser propagates back to every caller, so guarding
        // this method's own argument would make each logged variable "possibly null" at its
        // source and raise a null-dereference bug on code that cannot actually receive null.
        String text = Objects.toString(value, NULL_PLACEHOLDER);
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH) + TRUNCATION_MARKER;
        }
        return text.replaceAll(CONTROL_CHARACTERS, REPLACEMENT);
    }
}
