package net.sourceforge.jwbf.mediawiki.actions.misc;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;

import net.sourceforge.jwbf.core.actions.RequestBuilder;
import net.sourceforge.jwbf.core.actions.util.HttpAction;
import net.sourceforge.jwbf.core.contentRep.ParsedPage;
import net.sourceforge.jwbf.core.internal.Checked;
import net.sourceforge.jwbf.mapper.JsonMapper;
import net.sourceforge.jwbf.mapper.JsonMapper.ToJsonFunction;
import net.sourceforge.jwbf.mapper.MediawikiModule;
import net.sourceforge.jwbf.mediawiki.ApiRequestBuilder;
import net.sourceforge.jwbf.mediawiki.MediaWiki;
import net.sourceforge.jwbf.mediawiki.MediaWiki.Version;
import net.sourceforge.jwbf.mediawiki.actions.util.MWAction;

public class ParsePage extends MWAction {
    public enum ParseProp {
        /** Gives the categories of the parsed wikitext. */
        categories,
        /** Gives the html version of the categories. */
        categorieshtml,
        /** Adds the title of the parsed wikitext. */
        displaytitle,
        /** Gives the external links in the parsed wikitext. */
        externallinks,
        /** Gives parsed &lt;head> of the page. */
        headhtml,
        /** Gives items to put in the &lt;head> of the page. */
        headitems,
        /** Gives the images in the parsed wikitext. */
        images,
        /**
         * Gives interwiki links in the parsed wikitext. Note: Section tree is
         * only generated if there are more than 4 sections, if the __TOC__
         * keyword is present, or if sections is explicitly requested and the
         * page contains headings/sub-headings (overrides __NOTOC__)
         */
        iwlinks,
        /** Gives the langlinks the parsed wikitext. */
        langlinks,
        /** Gives the html version of the languagelinks. */
        languageshtml,
        /**
         * Gives the html version of the PP report. Returns empty if disablepp
         * is true. 1.23+
         */
        limitreporthtml,
        /** Gives the internal links in the parsed wikitext. */
        links,
        /**
         * Gives the modules, scripts, styles and messages used parsed wikitext.
         * 1.24+
         */
        modules,
        /** If page was used, specify the ID of the revision parsed. */
        revid,
        /**
         * Gives the sections in the parsed wikitext (TOC, table of contents).
         */
        sections,
        /** Gives the templates in the parsed wikitext. */
        templates,
        /** Gives the parsed text of the wikitext. */
        text
    }

    private static final class ParsedPageWrapper {
        @JsonProperty
        ParsedPage parse;

        @JsonProperty
        Map<String, Map<String, String>> warnings;
    }

    private static final Logger log = LoggerFactory.getLogger(ParsePage.class);

    private static final JsonMapper MAPPER = new JsonMapper(new ToJsonFunction() {
        private final ObjectMapper mapper = new ObjectMapper().registerModule(new MediawikiModule());

        @Override
        public Object toJson(String jsonString, Class<?> clazz) {
            try {
                return mapper.readValue(jsonString, clazz);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    });
    private final boolean followRedirects;
    private final Optional<String> language;
    private final String page;
    private final Set<ParseProp> props;
    private final Optional<Integer> section;
    private final Version version;

    private ParsedPage parsedPage;

    public ParsePage(String page, Set<ParseProp> props, boolean followRedirects, Integer section, String language,
            Version version) {
        this.version = version;
        this.page = Checked.nonBlank(page, "page");
        this.props = Checked.nonNull(props, "page props");
        this.followRedirects = followRedirects;
        this.section = Optional.fromNullable(section);
        this.language = Optional.fromNullable(language);
    }

    public ParsePage(String page, Set<ParseProp> props, boolean followRedirects, Version version) {
        this(page, props, followRedirects, null, null, version);
    }

    @Override
    public HttpAction getNextMessage() {
        RequestBuilder requestBuilder = new ApiRequestBuilder()
            .action("parse")
            .formatJson()
            .param("page", MediaWiki.urlEncode(page))
            .param("prop", MediaWiki.urlEncode(Joiner.on("|").join(props)))
            .param("redirects", followRedirects)
            .param(version.greaterEqThen(Version.DEVELOPMENT) ? "disablelimitreport" : "disablepp", true); // TODO change to Version.MW1_27 once released

        if (section.isPresent()) {
            requestBuilder.param("section", section.get());
        }
        if (language.isPresent()) {
            requestBuilder.param("uselang", language.get());
        }
        return requestBuilder.buildGet();
    }

    public ParsedPage getResult() {
        return parsedPage;
    }

    @Override
    public String processReturningText(String s, HttpAction hm) {
        this.parsedPage = parse(s);
        return null;
    }

    @VisibleForTesting
    static ParsedPage parse(String s) {
        ParsedPageWrapper wrapper = MAPPER.get(s, ParsedPageWrapper.class);
        if (wrapper.warnings != null && !wrapper.warnings.isEmpty()) {
            log.info("Got warning: '{}'", wrapper.warnings);
        }
        return wrapper.parse;
    }
}
