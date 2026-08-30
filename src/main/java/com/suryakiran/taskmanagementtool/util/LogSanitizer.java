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
 *
 * <p><strong>Email addresses need more than this.</strong> {@link #sanitize} makes a value
 * safe to write; it does not make it appropriate to write. An email address is personal
 * data, and logs are copied, shipped to a search index and retained far longer than the
 * record they describe. Prefer logging the user's id where a persisted user is in hand;
 * where there is no id yet — a registration before the insert, a forgotten-password request
 * for an address that may not resolve — use {@link #maskEmail} instead.</p>
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

    /** Logged in place of an empty or whitespace-only value, which {@code ""} would hide. */
    static final String BLANK_PLACEHOLDER = "<blank>";

    /** Stands in for the part of an address deliberately withheld from the log. */
    static final String MASK = "***";

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

    /**
     * Returns a masked, single-line rendering of an email address: the first character of
     * the local part, then {@value #MASK}, then the domain in full — {@code sam@gmail.com}
     * becomes {@code s***@gmail.com}.
     *
     * <p><strong>Why mask rather than drop.</strong> These log lines are read for two
     * things: noticing that the <em>same</em> address is retrying over and over, and seeing
     * which provider or corporate domain is involved. The mask keeps both — repeats still
     * collide, the domain is still there — while the identity behind the address does not
     * reach the log.</p>
     *
     * <p><strong>Why not a hash.</strong> A hash of an address is stable and the input space
     * is small enough to enumerate, so it remains a pseudonymous identifier rather than an
     * anonymous one; and it throws away the domain, which is the part an operator actually
     * uses. It costs the debuggability without buying the privacy.</p>
     *
     * <p>The value is put through {@link #sanitize} first, so masking is applied
     * <em>in addition</em> to control-character stripping and truncation, never instead of
     * it — an address is still attacker-controlled input. A value with no {@code @} is not
     * an address this method can reason about and is withheld entirely; {@code null} and
     * blank values render as placeholders rather than throwing.</p>
     *
     * @param value the untrusted address; may be {@code null}
     * @return the masked text, never {@code null} and never containing a line break
     */
    public static String maskEmail(Object value) {
        // sanitize() rather than a null branch of our own, for the reason given above:
        // testing this method's own argument against null is a fact the analyser carries
        // back to every call site.
        String text = sanitize(value).trim();
        if (NULL_PLACEHOLDER.equals(text)) {
            return NULL_PLACEHOLDER;
        }
        if (text.isEmpty()) {
            return BLANK_PLACEHOLDER;
        }
        // lastIndexOf: a quoted local part may legally contain '@', and the domain is
        // whatever follows the final one.
        int at = text.lastIndexOf('@');
        if (at < 0) {
            return MASK;
        }
        // min(1, at) keeps the first character when there is one, and keeps nothing for an
        // address that begins with '@', without a second branch.
        return text.substring(0, Math.min(1, at)) + MASK + text.substring(at);
    }
}
