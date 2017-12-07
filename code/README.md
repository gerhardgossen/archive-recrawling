# Archive Re-Crawler


## Seed URLs

### Wikipedia

TODO ...

### Grep through CDX files

1. Add search keywords to last column of `topics.tsv`
2. Run `java de.l3s.icrawl.crawler.tools.QueryKeywordsCreator topics.tsv > patterns` to create query patterns.
  This creates OR-patterns for typical German URLs (drop accents, ASCIIfy umlauts, URL-encode special characters).
  Spaces are replaced by a `.` to match any character.
3. Copy `patterns` to cluster server.
4. Execute on cluster:

    ~~~~
    for l in `cat patterns`; do
        topic=${l%%  *};              # note: TAB character
        pattern=${l##*      };        # note: TAB character
        hadoop org.apache.hadoop.examples.Grep /data/ia/derivatives/de/cdx/TA/,/data/ia/derivatives/de/cdx/TB/ /tmp/gossen-urls-${topic}/  \' [^ ]*\\b${pattern}\\b.*?\\s+\';
    done
    ~~~~
    
5. Copy URL files to folder with crawl specifications:
    
    ~~~~
    for f in $(xls -d /tmp/gossen-urls-* | awk '{print $8}'); do
        n=`echo $f | cut -s -d'-' -f3`;
        xcat $f/part-* | awk '{print $2}' >  $n.txt;
    done
    ~~~~
    
6. Run `java de.l3s.icrawl.crawler.tools.MergeExternalUrls **specsDirectory**` to create merged crawl specifications.

