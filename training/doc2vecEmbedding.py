"""
Exploration of doc2vec training on a subset of our data.
The model produces embedding for the document but also for each word !
"""
import gensim
import pandas as pd
import os
from tqdm import tqdm

restrictSize = 10000
class MyCorpus(object):
    """An interator that yields sentences (lists of str)."""
    def __init__(self):
        self.data =pd.read_csv(os.path.join("data3","exact_hits.csv"),sep="\t",nrows=restrictSize)
    def __iter__(self):
        target_context_data = self.data.values[:restrictSize,2]

        for i,line in enumerate(target_context_data):
            # assume there's one document per line, tokens separated by whitespace ... #todo: use bert tokenization? to have sound comparisons....
            tokens = gensim.utils.simple_preprocess(line)
            yield gensim.models.doc2vec.TaggedDocument(tokens, [i])

train_corpus = MyCorpus()
model = gensim.models.doc2vec.Doc2Vec(vector_size = 100, min_count= 2,epochs = 40)
model.build_vocab(train_corpus)
print("created vocab ")
model.train(train_corpus,total_examples = model.corpus_count, epochs = model.epochs)
print("ended training")

from sklearn.manifold import TSNE                   # final reduction
import numpy as np                                  # array handling

num_dimensions = 2  # final num dimensions (2D, 3D, etc)
vectors = model.docvecs.vectors_docs
tsne = TSNE(n_components=num_dimensions, random_state=0)
vectors = tsne.fit_transform(vectors)
x_vals = vectors[:,0]
y_vals = vectors[:,1]

df_target = pd.DataFrame(vectors)
df_target.to_csv(os.path.join("data3","doc2vec_target_context.csv"))


import matplotlib.pyplot as plt
from matplotlib.cm import get_cmap
import random

target_link_data = train_corpus.data.values[:restrictSize,3]
target_names = np.array([str(c).split(".tgz!")[0] for c in target_link_data])
unique_database = np.unique(target_names)
# For every text_link, we assign to it a label that corresponds to its unique_database
labels_for_text_link = np.zeros(len(target_names))
for idx,base in  enumerate(unique_database):
    labels_for_text_link = np.where(target_names==base,idx,labels_for_text_link)
labels_for_text_link_norm = labels_for_text_link/unique_database.shape[0] #normalize
cm = get_cmap('hsv')
fig, ax = plt.subplots()
for idx,base in  enumerate(unique_database):
    fig, ax = plt.subplots()
    x=x_vals[np.where(target_names==base)]
    y=y_vals[np.where(target_names==base)]
    ax.scatter(x,y,c=cm(labels_for_text_link_norm[np.where(target_names==base)]))
    plt.show()

class MyLinkContextCorpus(object):
    """An interator that yields sentences (lists of str)."""
    def __init__(self):
        self.data =pd.read_csv(os.path.join("data3","exact_hits.csv"),sep="\t",nrows=restrictSize)
    def __iter__(self):
        link_context_data = self.data.values[:restrictSize,1]
        link_data = self.data.values[:restrictSize,0]
        for i,line in enumerate(link_context_data):
            # assume there's one document per line, tokens separated by whitespace ... #todo: use bert tokenization? to have sound comparisons....
            line.replace("<<LNK>>",link_data[i])
            tokens = gensim.utils.simple_preprocess(line)
            yield gensim.models.doc2vec.TaggedDocument(tokens, [i])

link_text_corpus = MyLinkContextCorpus()
iter_link_text_corpus = link_text_corpus.__iter__()
embedded_lincontext_tbl = []
for e in tqdm(iter_link_text_corpus):
    embedded_lincontext_tbl += [model.infer_vector(e.words)]
embedded_lincontext_tbl = np.array(embedded_lincontext_tbl)
df_linkcontext = pd.DataFrame(embedded_lincontext_tbl)
df_linkcontext.to_csv(os.path.join("data3","embedded_lincontext_tbl.csv"))


from scipy.spatial import cKDTree
#Without any TSNE embedding:
#Let us compute the k-accuracy for k in [1,5,10,50]
Y_tree = cKDTree(model.docvecs.vectors_docs)
k_values = [1,5,10,50]
scores = []
for k in k_values:
    queryResultd,queryResultIdx = Y_tree.query(embedded_lincontext_tbl,k)
    indexRange = np.column_stack([np.arange(0,embedded_lincontext_tbl.shape[0]) for _ in range(0,k)])
    if k>1:
        boolean = (queryResultIdx==indexRange)
        scores += [np.sum(np.sum(boolean,axis=1))/embedded_lincontext_tbl.shape[0]]
    else:
        boolean = queryResultIdx==np.squeeze(indexRange)
        scores += [np.sum(boolean)/embedded_lincontext_tbl.shape[0]]
plt.bar(range(1,5),scores)
plt.xticks(range(1,5),k_values)
plt.xlabel("k-neighbor selected")
plt.ylabel("accuracy for doc2vec 100-dimension")
plt.show()

y_tsne = np.stack((x_vals,y_vals),axis=1)
Y_tree_tsne = cKDTree(y_tsne)
link_tsne = tsne.fit_transform(embedded_lincontext_tbl)
scoresTSNE = []
for k in k_values:
    queryResultd,queryResultIdx = Y_tree_tsne.query(link_tsne,k)
    indexRange = np.column_stack([np.arange(0,link_tsne.shape[0]) for _ in range(0,k)])
    if k>1:
        boolean = (queryResultIdx==indexRange)
        scoresTSNE += [np.sum(np.sum(boolean,axis=1))/link_tsne.shape[0]]
    else:
        boolean = queryResultIdx==np.squeeze(indexRange)
        scoresTSNE += [np.sum(boolean)/link_tsne.shape[0]]
plt.bar(range(1,5),scoresTSNE)
plt.xticks(range(1,5),k_values)
plt.xlabel("k-neighbor selected")
plt.ylabel("accuracy for doc2vec reduced with TSNE to 2-dimension")
plt.show()

