# Preprocessing

## Prequisites

To run the preprocessor, JDK 1.8+ and Python 2+ are required. When running on Compute Canada, remember to call `module load java` first.

## Preparation

1. Run `python `[`./downloader.py`](https://github.com/breandan/tracelink/blob/master/preprocessing/downloader.py) to download `.tgz` files into the `archives/` directory.

2. Run `./gradlew ParseLinks -q [-Pretty] > links.csv` to extract the links. The optional `-Pretty` flag indicates whether the output should be padded.

3. Run `./gradlew ParseQueries -q -Process=links.csv > `[`links_with_top_k_count_search_candidates.csv`](links_with_top_k_count_search_candidates.tsv) to build the index and compute the count-search results.

## Extraction

The following regular expressions are used for link extraction:

```regex
val MIN_ALPHANUMERICS = 5
//                          ALLOW BALANCED PUNCTUATION UP TO ONE LEVEL OF NESTING ONLY
val BALANCED_BRACKETS = "((\\([^\\(\\)]\\))|(\\[[^\\[\\]]\\])|(\\{[^\\{\\}]\\})|(<[^<>]>)|(\"[^\"]\")|('[^']'))*"
//                   ANYTHING BUT: '"()[]{}<>                            ANYTHING BUT: '"()[]{}<>
val TEXT_OR_CODE = "[^'\"\\s\\(\\)\\{\\}\\[\\]<>]*[a-zA-Z._:&@#\\*~]{$MIN_ALPHANUMERICS,}[^'\"\\s\\(\\)\\{\\}\\[\\]<>]*"
val VALID_PHRASE = "$TEXT_OR_CODE$BALANCED_BRACKETS($TEXT_OR_CODE)*"
//                                      LINK URI       FRAGMENT               ANCHOR TEXT
val LINK_REGEX = Regex("<a[^<>]*href=\"([^<>#:?\"]*?)(#[^<>#:?\"]*)?\"[^<>]*>($VALID_PHRASE)</a>")

val asciiRegex = Regex("[ -~]*")
```

All links are validated and point to a known document in the same docset.

# Python Docsets

```
Airflow.tgz
Angr.tgz
Astropy.tgz
Cython.tgz
GAE-Python.tgz
Google_Cloud_Client-Python.tgz
Keras.tgz
Kivy.tgz
MNE.tgz
Mrjob.tgz
NLTK.tgz
Numba.tgz
Peewee.tgz
PyGame.tgz
PyGraphviz.tgz
PyInstaller.tgz
PyMel.tgz
PyMongo.tgz
PyTables.tgz
PyTorch.tgz
Pydial.tgz
Pygments.tgz
Pyramid.tgz
Pythonista.tgz
Qt_for_Python.tgz
Requests.tgz
Scikit-image.tgz
Scrapy.tgz
Seaborn.tgz
Six.tgz
Sphinx.tgz
SymPy.tgz
TensorFlow.tgz
Theano.tgz
click.tgz
cvxpy.tgz
formencode.tgz
gensim.tgz
lxml.tgz
mlpy.tgz
pymatgen.tgz
pyro.tgz
pysam.tgz
pyside2.tgz
pyspark.tgz
pytest.tgz
python-telegram-bot.tgz
scikit-learn.tgz
wxPython.tgz
```