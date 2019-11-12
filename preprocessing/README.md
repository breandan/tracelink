# Preprocessing

## Prequisites

Java 1.8+, Python 3.

## Preparation

1. Run `python `[`./downloader.py`](https://github.com/breandan/tracelink/blob/master/preprocessing/downloader.py) to download files into the `archives/` directory.

2. Run `./gradlew run > dataset.csv` to extract the links.

## Regex

The following regular expression was used to extract links on a [per-line](https://github.com/breandan/tracelink/blob/69d3207f1ed67520f32ca8c1670cbcd40970b897/preprocessing/src/main/kotlin/ParseLinks.kt#L63) basis:

```regex
<a[^<>]*href=\"([^<>#:]*?)(#[^\"]*)?\"[^<>]*>([!-;?-~]{6,})</a>
```
