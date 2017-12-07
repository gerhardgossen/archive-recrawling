package de.l3s.icrawl.util;

public class HtmlParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public HtmlParseException(String url, Throwable cause) {
        super("Failed to parse URL '" + url + "'", cause);
    }
}
