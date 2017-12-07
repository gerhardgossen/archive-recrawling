package de.l3s.icrawl.crawler.urls;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class RegexUrlNormalizer implements UrlNormalizer {
    private static class RegexReplaceRule {
        private final Pattern pattern;
        private final String replacement;

        public RegexReplaceRule(String pattern, String replacement) {
            this.pattern = Pattern.compile(pattern);
            this.replacement = replacement;
        }

        public String apply(String input) {
            return pattern.matcher(input).replaceAll(replacement);
        }

    }

    private final List<RegexReplaceRule> rules;

    public RegexUrlNormalizer(URL normalizerRulesResource) throws IOException {
        try (InputStream is = normalizerRulesResource.openStream()) {
            Document rulesDoc = Jsoup.parse(is, "UTF-8", "");
            rules = new ArrayList<>();
            for (Element e : rulesDoc.select("regex")) {
                String pattern = e.select("pattern").first().text();
                String replacement = e.select("substitution").first().text();
                rules.add(new RegexReplaceRule(pattern, replacement));
            }
        }
    }

    @Override
    public String normalize(String url) {
        String result = url;
        for (RegexReplaceRule rule : rules) {
            result = rule.apply(result);
        }
        return result;
    }

}
