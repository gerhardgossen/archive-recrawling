package de.l3s.icrawl.crawler.urls;

import io.mola.galimatias.GalimatiasParseException;
import io.mola.galimatias.NameValue;
import io.mola.galimatias.URL;
import io.mola.galimatias.URLSearchParameters;
import io.mola.galimatias.canonicalize.CombinedCanonicalizer;
import io.mola.galimatias.canonicalize.RFC3986Canonicalizer;
import io.mola.galimatias.canonicalize.StripPartCanonicalizer;
import io.mola.galimatias.canonicalize.StripPartCanonicalizer.Part;
import io.mola.galimatias.canonicalize.URLCanonicalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

public class UrlCanonicalizerNormalizer implements UrlNormalizer {
    private static final Pattern EXCLUDE_PATTERN = Pattern.compile("^utm_|sess(ion)?id", Pattern.CASE_INSENSITIVE);

    public static class StripParametersCanonicalizer implements URLCanonicalizer {
        private final Pattern excludePattern;

        public StripParametersCanonicalizer(Pattern excludePattern) {
            this.excludePattern = excludePattern;
        }

        @Override
        public URL canonicalize(URL url) throws GalimatiasParseException {
            return url.withQuery(canonicalizeQuery(new URLSearchParameters(url.query())));
        }

        private String canonicalizeQuery(URLSearchParameters searchParameters) {
            ImmutableMultimap.Builder<String, String> finalParameters = ImmutableMultimap.builder();
            for (NameValue param : searchParameters) {
                if (!excludePattern.matcher(param.name()).find()) {
                    finalParameters.put(param.name(), param.value());
                }
            }
            Multimap<String, String> includedParameters = finalParameters.build();
            if (includedParameters.isEmpty()) {
                return null;
            }
            List<String> sortedKeys = new ArrayList<>(includedParameters.keySet());
            Collections.sort(sortedKeys);
            final StringBuilder sb = new StringBuilder(100);
            for (String key : sortedKeys) {
                for (String value : includedParameters.get(key)) {
                    if (sb.length() > 0) {
                        sb.append('&');
                    }
                    sb.append(key);
                    if (!value.isEmpty()) {
                        sb.append('=');
                        sb.append(value);
                    }

                }
            }
            return sb.toString();
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(UrlCanonicalizerNormalizer.class);
    private final URLCanonicalizer canonicalizer = new CombinedCanonicalizer(
        new StripParametersCanonicalizer(EXCLUDE_PATTERN),
        new StripPartCanonicalizer(Part.FRAGMENT),
        new RFC3986Canonicalizer());

    @Override
    public String normalize(String url) {
        URL parsedUrl;
        try {
            parsedUrl = URL.parse(url);
        } catch (GalimatiasParseException e) {
            logger.trace("Invalid URL '{}', dropping", url, e);
            return null;
        }
        try {
            return canonicalizer.canonicalize(parsedUrl).toString();
        } catch (GalimatiasParseException e) {
            logger.debug("Could not canonicalize URL '{}', returning unchanged ", url, e);
            return url;
        }
    }

}
