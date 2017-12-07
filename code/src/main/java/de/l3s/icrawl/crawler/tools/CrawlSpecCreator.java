package de.l3s.icrawl.crawler.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;

import de.l3s.icrawl.contentanalysis.DocumentVectorSimilarity;
import de.l3s.icrawl.contentanalysis.LanguageModels;
import de.l3s.icrawl.crawler.ArchiveCrawlSpecification;
import de.l3s.icrawl.crawler.ArchiveCrawler;
import de.l3s.icrawl.crawler.TimeSpecification;
import de.l3s.icrawl.domain.specification.NamedEntity;
import de.l3s.icrawl.util.TextExtractor;
import net.sourceforge.jwbf.core.actions.HttpActionClient;
import net.sourceforge.jwbf.core.contentRep.ParsedPage;
import net.sourceforge.jwbf.core.contentRep.ParsedPage.Link;
import net.sourceforge.jwbf.mediawiki.MediaWiki;
import net.sourceforge.jwbf.mediawiki.MediaWiki.Version;
import net.sourceforge.jwbf.mediawiki.actions.misc.ParsePage;
import net.sourceforge.jwbf.mediawiki.actions.misc.ParsePage.ParseProp;
import net.sourceforge.jwbf.mediawiki.actions.queries.CategoryMembersSimple;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import static com.google.common.io.Resources.getResource;
import static java.util.Collections.emptySet;

public class CrawlSpecCreator {
    private class CrawlSpecBuilder {
        private final Multiset<String> keywords = HashMultiset.create();
        private final Map<String, Locale> documents = new HashMap<>();
        private final Set<String> urls = new HashSet<>();
        private final List<String> referenceDocuments = new ArrayList<>();
        private final TimeSpecification timeSpecification;
        private final String name;
        private final String description;

        public CrawlSpecBuilder(String name, String description, TimeSpecification timeSpecification) {
            this.name = name;
            this.description = description;
            this.timeSpecification = timeSpecification;

        }

        public void addUrls(Collection<String> urls) {
            urls.stream().map(CrawlSpecCreator::cleanUrl).filter(CrawlSpecCreator::isAllowedUrl).forEach(this.urls::add);
        }

        public void addReferenceDocument(String url) {
            referenceDocuments.add(url);
        }

        public void addUrl(String url) {
            String cleanedUrl = cleanUrl(url);
            if (isAllowedUrl(cleanedUrl)) {
                urls.add(cleanedUrl);
            }
        }

        public void addKeyword(String keyword) {
            keywords.add(keyword);
        }

        public void addDocument(String text, Locale locale) {
            documents.put(text, locale);
        }

        public ArchiveCrawlSpecification createSpec(boolean includeKeywords) {
            Set<String> usedKeywords = includeKeywords ? keywords.elementSet() : emptySet();
            LanguageModels models = new LanguageModels(Locale.GERMAN, idfDictionary, DEFAULT_LANGUAGE);
            DocumentVectorSimilarity dvs = new DocumentVectorSimilarity(documents, usedKeywords, new HashSet<NamedEntity>(), 100,
                false, DEFAULT_LANGUAGE, models);
            Map<Locale, Set<String>> keywordsByLanguage = ImmutableMap.of(Locale.GERMAN, usedKeywords);

            return new ArchiveCrawlSpecification(name, new ArrayList<>(urls), referenceDocuments, timeSpecification,
                dvs.getReferenceVectors(), keywordsByLanguage, description, DEFAULT_LANGUAGE, dvs.getCorrectionFactors());
        }
    }

    private static final String WIKIPEDIA_API_URL = "https://de.wikipedia.org/w/";
    private static final String WIKIPEDIA_BASE_URL = "https://de.wikipedia.org/wiki/";
    private static final String WIKINEWS_API_URL = "https://de.wikinews.org/w/";
    private static final Logger logger = LoggerFactory.getLogger(CrawlSpecCreator.class);
    private static final Locale DEFAULT_LANGUAGE = Locale.GERMAN;
    private static final Map<Pattern, String> URL_REPLACEMENTS = ImmutableMap
        .<Pattern, String> builder()
        .put(Pattern.compile("https://web.archive.org/web/\\d+/(.*)"), "$1")
        .put(Pattern.compile("https://archive.is/\\d+/(.*)"), "$1")
        .put(Pattern.compile("https://archive.is/(.*)\\*$"), "$1")
        .put(Pattern.compile("http://www.webcitation.org/[a-zA-Z0-9]+\\?url=(.*)"), "$1")
        .put(Pattern.compile("http://derefer.unbubble.eu/?\\?u=(.*)"), "$1")
        .put(Pattern.compile("http://deadurl.invalid/(.*)"), "$1")
        .build();
    private static final Set<Pattern> URL_PATTERNS_WHITELIST = ImmutableSet.of(Pattern.compile("^https?://[a-z0-9.-]*?\\.de/"));
    private final MediaWikiBot wpBot;
    private final MediaWikiBot wnBot;
    private final EnumSet<ParseProp> props;
    private final Pattern parentheses;
    private final Map<String, Double> idfDictionary;
    private final Version version;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java " + CrawlSpecCreator.class.getName() + " topicsFile.tsv [outputDirectory]");
            System.exit(1);
        }
        CrawlSpecCreator creator = new CrawlSpecCreator();
        DateTimeFormatter dateFormat = DateTimeFormatter.ISO_DATE;
        File baseDirectory = new File(args.length > 1 ? args[1] : "");
        baseDirectory.mkdirs();
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
                LocalDate from = LocalDate.parse(parts[1], dateFormat);
                LocalDate until = LocalDate.parse(parts[2], dateFormat);
                Period before = Period.parse(parts[3]);
                Period after = Period.parse(parts[4]);
                String description = parts[5];
                List<String> wikipedia = Arrays.asList(parts[6].split(",\\s*"));
                creator.extract(code, wikipedia, from, until, before, after, description, baseDirectory);
                logger.info("Created crawl spec for topic {}", code);
            }
        }
    }

    public CrawlSpecCreator() throws IOException {
        URL wikiUrl = new URL(WIKIPEDIA_API_URL);
        HttpClient httpClient = HttpClientBuilder
            .create()
            .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
            .setUserAgent("L3SSpecBuilder <gossen@l3s.de>")
            .build();
        wpBot = new MediaWikiBot(
            HttpActionClient.builder().withClient(httpClient).withUrl(wikiUrl).withRequestsPerUnit(10, TimeUnit.MINUTES).build());
        wnBot = new MediaWikiBot(HttpActionClient
            .builder()
            .withClient(httpClient)
            .withUrl(WIKINEWS_API_URL)
            .withRequestsPerUnit(1, TimeUnit.SECONDS)
            .build());
        version = wpBot.getVersion();
        // logger.info("Accessing MediaWiki version {}",
        // bot.getPerformedAction(new GetVersion()).getGenerator());
        props = EnumSet.of(ParseProp.externallinks, ParseProp.links, ParseProp.text);
        parentheses = Pattern.compile(" (\\([^)]+\\))$");

        try (InputStream is = new GZIPInputStream(getResource(ArchiveCrawler.IDF_DICTIONARY_DE).openStream())) {
            idfDictionary = LanguageModels.readIdfDictionary(is);
        }
    }

    public void extract(String name, Collection<String> pages, LocalDate from, LocalDate until, Period beforeFuzziness,
            Period afterFuzziness, String description, File baseDirectory) throws IOException {
        CrawlSpecBuilder builder = new CrawlSpecBuilder(name, description,
            TimeSpecification.interval(from, until, beforeFuzziness, afterFuzziness));
        for (String pageTitle : pages) {
            if (pageTitle.startsWith("news:")) {
                extractWikiNewsCategory(pageTitle.substring("news:".length()), builder);
            } else {
                extractWikipediaPage(pageTitle, builder);
            }
        }
        builder.createSpec(true).writeFile(new File(baseDirectory, name + ".json"));

        builder.createSpec(false).writeFile(new File(baseDirectory, name + "-noKW.json"));

    }

    private void extractWikipediaPage(String pageTitle, CrawlSpecBuilder builder) {
        builder.addReferenceDocument(WIKIPEDIA_BASE_URL + pageTitle);
        ParsedPage page = wpBot.getPerformedAction(new ParsePage(pageTitle, props, true, version)).getResult();
        for (String url : page.getExternalLinks()) {
            if (!url.startsWith("//")) {
                builder.addUrl(url);
            }
        }
        for (Link link : page.getLinks()) {
            String linkName = link.getName();
            if (!linkName.startsWith("Liste ") && !linkName.startsWith("Vorlage:")) {
                builder.addKeyword(parentheses.matcher(linkName).replaceFirst(""));
            }
        }
        String cleanedText = "";
        Document fragment = parseHtmlFragment(page.getText());
        if (!page.getText().trim().isEmpty() && fragment != null) {
            cleanedText = cleanWikipediaHtml(fragment);
        }
        builder.addDocument(cleanedText, Locale.GERMAN);
    }

    private void extractWikiNewsCategory(String categoryName, CrawlSpecBuilder builder) {
        logger.debug("Retrieving WikiNews category {}", categoryName);
        for (String title : new CategoryMembersSimple(wnBot, categoryName, MediaWiki.NS_MAIN)) {
            List<String> externalLinks = wnBot
                .getPerformedAction(new ParsePage(title, EnumSet.of(ParseProp.externallinks), false, version))
                .getResult()
                .getExternalLinks();
            logger.debug("Got {} links for '{}': {}", externalLinks.size(), title, externalLinks);
            builder.addUrls(externalLinks);
        }
    }

    @VisibleForTesting
    static boolean isAllowedUrl(String url) {
        boolean inWhitelist = false;
        for (Pattern whitelistPattern : URL_PATTERNS_WHITELIST) {
            if (whitelistPattern.matcher(url).find()) {
                inWhitelist = true;
                break;
            }
        }
        return inWhitelist;
    }

    @VisibleForTesting
    static String cleanUrl(String url) {
        String cleanedUrl = url;
        for (Entry<Pattern, String> replacement : URL_REPLACEMENTS.entrySet()) {
            cleanedUrl = replacement.getKey().matcher(cleanedUrl).replaceAll(replacement.getValue());
        }
        if (logger.isDebugEnabled() && !cleanedUrl.equals(url)) {
            logger.debug("Replaced URL {} with {}", url, cleanedUrl);
        }
        return cleanedUrl;
    }

    private String cleanWikipediaHtml(Document fragment) {
        for (Element blockElement : fragment.body().children()) {
            if (!blockElement.tagName().equals("p")) {
                blockElement.remove();
            }
        }
        return domFragmentToString(fragment);
    }

    Document parseHtmlFragment(String wikipediaHtml) {
        return Jsoup.parseBodyFragment(wikipediaHtml);
    }

    private String domFragmentToString(Document doc) {
        return TextExtractor.extractText(doc);
    }

}
