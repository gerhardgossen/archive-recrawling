# Zip file output format

The zip file output format for extracted archive subsets is a standard .zip file containing:

- the extracted source files (named `0.html`, `1.html`, ...). **Note that all files are re-encoded to UTF-8 to ease further processing, even if the HTML meta tags disagree.**
- an overview file (`urls.csv`).
- a file listing URLs that would have been included, but are missing from the archive (`missing.csv`)

The latter two files have the following columns:

## `urls.csv`

| column    | description                                               |
|:----------|:----------------------------------------------------------|
| url       | original URL of the document                              |
| path      | crawl path for reaching the document (`S`=seed, `L`=link) |
| relevance | estimated relevance of the document ([0.0, 1.0])          |
| crawlTime | time the document was retrieved from the web (ISO 8601)   |
| file      | name of file in .zip                                      |

## `missing.csv`

| column   | description                                               |
|:---------|:----------------------------------------------------------|
| url      | original URL of the document                              |
| path     | crawl path for reaching the document (`S`=seed, `L`=link) |
| priority | estimated relevance of the document ([0.0, 1.0])          |

