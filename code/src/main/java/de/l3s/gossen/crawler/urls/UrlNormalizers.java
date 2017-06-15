package de.l3s.gossen.crawler.urls;

import java.util.Arrays;
import java.util.List;

public class UrlNormalizers implements UrlNormalizer {

    private final List<UrlNormalizer> normalizers;

    public UrlNormalizers(UrlNormalizer... normalizers) {
        this(Arrays.asList(normalizers));
    }

    public UrlNormalizers(List<UrlNormalizer> normalizers) {
        this.normalizers = normalizers;
    }

    @Override
    public String normalize(String url) {
        String processed = url;
        for (UrlNormalizer urlNormalizer : normalizers) {
            if (processed != null) {
                processed = urlNormalizer.normalize(processed);
            }
        }
        return processed;
    }

}
