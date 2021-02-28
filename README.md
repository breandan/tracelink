# TraceLink

The goal of this project is to link source code to documentation and other realated software artifacts. We train a recommender system that suggests a list of documents sorted by their relevance to a given context or code snippet. For uncommon tokens, this should at least include all documents which refer to the token directly (e.g. an inverted index), as well as documents which are semantically or contextually related to the source code in non-obvious ways.

## Approach

We train a variational autoencoder and use the encoder to project short sequences of text with their accompanying link into *link space*. In the same manner, we train a second VAE on documents, to learn a *document space* embedding. Finally we train a supervised model from link space to document space, i.e. to predict the document(s) which a link with unknown destination may have targeted.

## Datasets

The following datasets are used to extract relevant links from documentation:

* [Zeal User Contributed Docsets](https://zealusercontributions.now.sh/)

StackExchange contains a large dataset of programming related Q&A:

* [Stack Exchange Createive Commons](https://archive.org/details/stackexchange)

It may be interesting to explore code search and suggestion, in a similar manner.

* [GitHub CodeSearchNet Challenge](https://github.com/github/CodeSearchNet)

## [Preprocessing](preprocessing/README.md)

Links matching a simple pattern are collected from API documentation.

## Sample

The following is an excerpt from the post-processed documentation dataset: 

```
link	context	source	target	fragment
"qgsprocessingalgorithm.h:223"	"orithm::groupIdvirtual QString groupId() constReturns the unique ID of the group this algorithm belongs to. Definition:  <<LNK>> "	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/qgsalgorithmswapxy_8h_source.html"	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/qgsprocessingalgorithm_8h_source.html"	"#l00223"
"QgsProcessingFeatureBasedAlgorithm"	" <<LNK>> An abstract QgsProcessingAlgorithm base class for processing algorithms which operate "feature-by-fea...Definition: qgsp"	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/qgsalgorithmswapxy_8h_source.html"	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/classQgsProcessingFeatureBasedAlgorithm.html"	""
"qgsprocessingalgorithm.h:867"	"ithmAn abstract QgsProcessingAlgorithm base class for processing algorithms which operate "feature-by-fea...Definition:  <<LNK>> "	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/qgsalgorithmswapxy_8h_source.html"	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/qgsprocessingalgorithm_8h_source.html"	"#l00867"
```

## Experiments

* Compare doc2vec with keyphrase / bag-of-words extraction.
* Compare in-vocabulary to out-of-vocabulary retrieval precision.
* Stack trace entity alignment to e.g. GitHub lines of code.
* IDE based context alignment to e.g. StackOverflow issues.

## References

* [Lancer: Your Code Tell Me What You Need](http://www.cs.sjtu.edu.cn/~zhonghao/paper/Lancer.pdf), Zhou et al. (2019) [[source code](https://github.com/sfzhou5678/Lancer)]
* [TraceSim: A Method for Calculating Stack Trace Similarity](https://arxiv.org/pdf/2009.12590.pdf), Vasiliev et al. (2020)
* [Exploiting Code Knowledge Graph for Bug Localization via Bi-directional Attention](https://doi.org/10.1145/3387904.3389281), Zhang et al. (2020)
