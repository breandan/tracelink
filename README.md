# Code2Doc Trace Link Retrieval

## Dataset

[Zeal User Contributed Docsets](https://zealusercontributions.now.sh/)

## Preprocessing

Links are in the following format. See [parser](/parser).

## Sample

```
link	context	source	target	fragment
"qgsprocessingalgorithm.h:223"	"orithm::groupIdvirtual QString groupId() constReturns the unique ID of the group this algorithm belongs to. Definition:  <<LNK>> "	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/qgsalgorithmswapxy_8h_source.html"	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/qgsprocessingalgorithm_8h_source.html"	"#l00223"
"QgsProcessingFeatureBasedAlgorithm"	" <<LNK>> An abstract QgsProcessingAlgorithm base class for processing algorithms which operate "feature-by-fea...Definition: qgsp"	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/qgsalgorithmswapxy_8h_source.html"	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/classQgsProcessingFeatureBasedAlgorithm.html"	""
"qgsprocessingalgorithm.h:867"	"ithmAn abstract QgsProcessingAlgorithm base class for processing algorithms which operate "feature-by-fea...Definition:  <<LNK>> "	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/qgsalgorithmswapxy_8h_source.html"	"QGIS.tgz!/QGIS.docset/Contents/Resources/Documents/qgsprocessingalgorithm_8h_source.html"	"#l00867"
```

## Experiments

1. Compare doc2vec with keyphrase extraction.
2. Compare in-vocabulary to out-of-vocabulary.

## References

See...

## Cite us

David Venuto
Breandan Considine
Pierre Orhan