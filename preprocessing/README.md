# Preprocessing

## Prequisites

To run the preprocessor, JDK 1.8+ and Python 3+ are required.

## Preparation

1. Run `python `[`./downloader.py`](https://github.com/breandan/tracelink/blob/master/preprocessing/downloader.py) to download `.tgz` files into the `archives/` directory.

2. Run `./gradlew run > dataset.csv` to extract the links.

## Extraction

The following regular expression was used to extract links on a [per-line](https://github.com/breandan/tracelink/blob/69d3207f1ed67520f32ca8c1670cbcd40970b897/preprocessing/src/main/kotlin/ParseLinks.kt#L63) basis:

```regex
<a[^<>]*href=\"([^<>#:]*?)(#[^\"]*)?\"[^<>]*>([!-;?-~]{6,})</a>
```

All links are validated and point to a known document in the same docset.