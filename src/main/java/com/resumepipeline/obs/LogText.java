package com.resumepipeline.obs;

/** Short text previews for log messages — keeps long content (JD text, bullet text) off one line. */
public final class LogText {

    private LogText() {}

    public static String abbreviate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
