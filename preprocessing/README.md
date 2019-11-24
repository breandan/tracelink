# Preprocessing

## Prequisites

To run the preprocessor, JDK 1.8+ and Python 3+ are required.

## Preparation

1. Run `python `[`./downloader.py`](https://github.com/breandan/tracelink/blob/master/preprocessing/downloader.py) to download `.tgz` files into the `archives/` directory.

2. Run `./gradlew ParseLinks -q [-Pretty] > dataset.csv` to extract the links. The optional `-Pretty` flag indicates whether the output should be padded.

3. Run `./gradlew ParseDocs` to build the Lucene index.

## Extraction

The following regular expression was used to extract links on a [per-line](https://github.com/breandan/tracelink/blob/69d3207f1ed67520f32ca8c1670cbcd40970b897/preprocessing/src/main/kotlin/ParseLinks.kt#L63) basis:

```regex
//               LINK URI      FRAGMENT              ANCHOR TEXT
<a[^<>]*href=\"([^<>#:?\"]*?)(#[^<>#:?\"]*)?\"[^<>]*>([!-;?-~]{6,})</a>
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