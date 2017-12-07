package de.l3s.icrawl.crawler.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.common.escape.CharEscaperBuilder;
import com.google.common.escape.Escaper;
import com.google.common.io.Files;

import static com.google.common.net.UrlEscapers.urlPathSegmentEscaper;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.stripAccents;

public class QueryKeywordsCreator {
    private static final Escaper GERMAN_ESCAPER = new CharEscaperBuilder()
        .addEscape(' ', ".?")
        .addEscape('ä', "ae")
        .addEscape('ö', "oe")
        .addEscape('ü', "ue")
        .addEscape('ß', "ss")
        .toEscaper();

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java " + QueryKeywordsCreator.class.getName() + " topicsFile.tsv");
            System.exit(1);
        }

        try (BufferedReader reader = Files.newReader(new File(args[0]), StandardCharsets.UTF_8)) {
            boolean readHeader = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!readHeader) {
                    readHeader = true;
                    continue;
                }
                String[] parts = line.split("\t", 8);
                String code = parts[0];
                List<String> keywords = Arrays.asList(parts[7].split(",\\s*"));
                Set<String> queryKeywords = new HashSet<>();
                for (String keyword : keywords) {
                    String lcKeyword = keyword.toLowerCase(Locale.GERMAN);
                    queryKeywords.add(lcKeyword.replaceAll("\\s+", ".?"));
                    queryKeywords.add(GERMAN_ESCAPER.escape(lcKeyword));
                    queryKeywords.add(stripAccents(lcKeyword).replaceAll("\\s+", ".?"));
                    queryKeywords.add(urlPathSegmentEscaper().escape(lcKeyword).replaceAll("\\+", ".?"));
                }
                String query = queryKeywords.size() == 1 ? queryKeywords.iterator().next()
                        : queryKeywords.stream().collect(joining(")|(", "(", ")"));
                System.out.format("%s\t%s%n", code, query);
            }
        }
    }
}
