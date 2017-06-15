package de.l3s.gossen.crawler;

import java.time.ZonedDateTime;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class CrawlUrlTest {

    @Test
    public void testMerge() {
        CrawlUrl seedFoo = CrawlUrl.fromSeed("foo", 1f);
        CrawlUrl seedBar = CrawlUrl.fromSeed("bar", 1f);
        CrawlUrl fooBar = seedFoo.outlink("bar", 1f, ZonedDateTime.now());
        assertThat(seedFoo.merge(seedBar), is(sameInstance(seedFoo)));
        assertThat(seedBar.merge(fooBar), is(sameInstance(seedBar)));
        assertThat(fooBar.merge(seedBar), is(sameInstance(seedBar)));
    }

}
