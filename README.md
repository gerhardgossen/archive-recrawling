# Supplement to Web archive relevance paper #

This repository contains the data and code used in our paper “Towards the Extraction of Event-Centric Datasets
from Large Scale Temporal Web Collections” (under submission).

## Collection Specifications ##

The Collection Specifications are available in a [human readable](topics.md) and [machine readable](topics.tsv) form. The specification has the following fields:

- **Code**: name of the topic used in the paper
- **From**: start date of the event
- **Until**: end date of the event
- **Before**: ramp-up phase of the event in days
- **After**: cool-down phase of the event in days
- **Description**: short human-readable description of the event
- **Wikipedia**: one or more relevant pages from the German Wikipedia

The derived full Collection Specifications that include the actual reference vector are available in the directory [collection_specifications](collection_specifications/) in JSON format.


## Benchmark annotations ##

The relevance annotations for 12 selected topics are available in the directory [annotation](annotations/) as CSV files with the following fields:

Name | Description
:-----|:-------------
**campaign** | name of the topic
**url** | evaluated URL
**timestamp** | crawl time of the evaluated URL
**contentRelevance** | automatically computed content relevance (*TopicR*)
**timeRelevance** | automatically computed temporal relevance (*TempR*)
**vote** | manually annotated relevance of this document, one of “Highly relevant”, “Relevant”, “Not relevant”, “Junk or spam”, “Don’t know”
**comment** | optional annotator comment
