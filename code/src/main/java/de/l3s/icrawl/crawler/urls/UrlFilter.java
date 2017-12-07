package de.l3s.icrawl.crawler.urls;

import com.google.common.base.Predicate;

public interface UrlFilter extends Predicate<String> {
    UrlFilter ACCEPT_ALL = new UrlFilter() {
        @Override
        public boolean apply(String input) {
            return input != null;
        }
    };
    UrlFilter ONLY_HTTP = new UrlFilter() {
        @Override
        public boolean apply(String url) {
            return url != null && (url.startsWith("http:") || url.startsWith("https:"));
        }
    };
}
