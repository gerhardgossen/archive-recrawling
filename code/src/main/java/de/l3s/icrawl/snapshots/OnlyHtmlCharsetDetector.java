package de.l3s.icrawl.snapshots;

import java.io.IOException;
import java.util.Locale;

import org.archive.format.http.HttpHeaders;
import org.archive.format.text.charset.CharsetDetector;
import org.archive.format.text.charset.StandardCharsetDetector;

/**
 * Charset detector for HTML files.
 *
 * Will extract a charset for all files from the <tt>Content-Type</tt> header.
 * For HTML files also the content is used to find the charset.
 * 
 * @see StandardCharsetDetector
 *
 */
public class OnlyHtmlCharsetDetector extends CharsetDetector {

    @Override
    public String getCharset(byte[] buffer, int len, HttpHeaders headers) throws IOException {
        String charSet = getCharsetFromHeaders(headers);
        if (charSet == null) {
            String contentType = headers.getValueCaseInsensitive(HTTP_CONTENT_TYPE_HEADER);
            if (contentType != null && contentType.toLowerCase(Locale.ENGLISH).contains("html")) {
                charSet = getCharsetFromMeta(buffer, len);
                if (charSet == null) {
                    charSet = getCharsetFromBytes(buffer, len);
                    if (charSet == null) {
                        charSet = DEFAULT_CHARSET;
                    }
                }
            } else {
                charSet = DEFAULT_CHARSET;
            }
        }
        return charSet;
    }

}
