"""
Created by Pierre Orhan 24/11/2019
Here we explore the embedding that we can retrieve by training a subset of the corpus.
Similar words are one that are in the same document.
Yet since we only obtain words embedding it is difficult to define a vector for each document...
    Some simple heuristic is to take the average of the words in the document
A more powerful idea is to use doc2vec strategy... or bert!
"""

import gensim
import pandas as pd
import os

restrictSize = 10000

class MyCorpus(object):
    """An interator that yields sentences (lists of str)."""
    def __iter__(self):
        data = pd.read_csv(os.path.join("data3","exact_hits.csv"),sep="\t",nrows=restrictSize)
        target_context_data = data.values[:restrictSize,2]
        for line in target_context_data:
            # assume there's one document per line, tokens separated by whitespace
            yield gensim.utils.simple_preprocess(line)

sentences = MyCorpus()
print("corpus ready")
model = gensim.models.Word2Vec(sentences=sentences,workers = 4 )
print("finished training")

from sklearn.manifold import TSNE                   # final reduction
import numpy as np                                  # array handling


def reduce_dimensions(model):
    num_dimensions = 2  # final num dimensions (2D, 3D, etc)

    vectors = [] # positions in vector space
    labels = [] # keep track of words to label our data again later
    for word in model.wv.vocab:
        vectors.append(model.wv[word])
        labels.append(word)

    # convert both lists into numpy vectors for reduction
    vectors = np.asarray(vectors)
    labels = np.asarray(labels)

    # reduce using t-SNE
    vectors = np.asarray(vectors)
    tsne = TSNE(n_components=num_dimensions, random_state=0)
    vectors = tsne.fit_transform(vectors)

    x_vals = [v[0] for v in vectors]
    y_vals = [v[1] for v in vectors]
    return x_vals, y_vals, labels

x_vals, y_vals, labels = reduce_dimensions(model)

x_vals = np.array(x_vals)
y_vals = np.array(y_vals)

import matplotlib.pyplot as plt
import random

random.seed(0)
plt.figure(figsize=(12, 12))
plt.scatter(x_vals, y_vals)
indices = list(range(len(labels)))
selected_indices = random.sample(indices, 25)
for i in selected_indices:
    plt.annotate(labels[i], (x_vals[i], y_vals[i]))

data = sentences.__iter__()
myLine = data.__next__()
line_labels_idx = []
for l in myLine:
    idx = np.where(labels==l)[0]
    if len(idx)>0:
        line_labels_idx+=[idx[0]]
line_labels_idx = np.asarray(line_labels_idx)
plt.scatter(x_vals[line_labels_idx],y_vals[line_labels_idx],c="orange")
for i in line_labels_idx:
    plt.annotate(labels[i], (x_vals[i], y_vals[i]))
plt.show()
