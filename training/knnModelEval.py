"""
Created by Pierre Orhan 26/11/2019

Here we load the result of either doc2vecEmbedding or bertEmbedding,
perform for each a set of training of regressions mechanism, and compare their performance using the metric file.
For ease of implementation, each training mechanism is abstracted in a separate file
    They should implement a function fit that gives the regressed embedded:
        training and testing context dataset, as well as the target dataset(always the same both for testing and training)...
"""
import os
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from training import metrics
from training import SimpleModels as sm

restrictSize = 1000
#Import data
data = pd.read_csv(os.path.join("data3","exact_hits.csv"),sep="\t",nrows=restrictSize)
data_doc2vec_target = pd.read_csv(os.path.join("data3","doc2vec_target_context.csv"),nrows=restrictSize)
doc2vec_target = data_doc2vec_target.values[:,1:]
data_doc2vec_link = pd.read_csv(os.path.join("data3","doc2vec_link_context.csv"),nrows=restrictSize)
doc2vec_link = data_doc2vec_link.values[:,1:]
data_bert_target = pd.read_csv(os.path.join("data3","bert_target_context.csv"),nrows=restrictSize)
bert_target = data_bert_target.values[:,1:]
data_bert_link = pd.read_csv(os.path.join("data3","bert_link_context.csv"),nrows=restrictSize)
bert_link = data_bert_link.values[:,1:]

#Create the knn filter datastrucutre (includes a word to doc dictionnary)
filterKnn = metrics.FilteredKnn(data)

num_dim = 2

#todo build the chains, build the parameter optimizer pipelines
models = [ sm.tsneModel(num_dim),
           sm.ccaModel(num_dim),
           sm.pcaModel(num_dim),
           sm.nnRegression(num_dim,10),
           sm.gpRegression(),
           sm.chain([sm.tsneModel,sm.nnRegression],[num_dim,[num_dim,10]]),
           sm.chain([sm.tsneModel,sm.gpRegression],[num_dim,]),
           sm.chain([sm.ccaModel,sm.nnRegression],[num_dim,[num_dim,10]]),
           sm.chain([sm.ccaModel,sm.gpRegression],[num_dim,]),
           sm.chain([sm.pcaModel,sm.nnRegression],[num_dim,[num_dim,10]]),
           sm.chain([sm.pcaModel,sm.gpRegression],[num_dim,])
]
#At first, let us evaluate the training results:
L = data.values[:,0]
ks = [1,5,10,25,50]
T = [50,100,200]
scoreMatrix = np.zeros((len(models),len(ks),len(T)))
EQ,ED = models[4].fit(doc2vec_target,doc2vec_link)
print(filterKnn.measure_knn_acc(50,5,L,EQ,ED))
print(filterKnn.raw_tf_accuracy(50,5,L))
# for idm,m in enumerate(models):
#     EQ,ED = m.fit(doc2vec_target,doc2vec_link)
#     # For different values of k and T, we get the model score:
#     for idk,k in enumerate(ks):
#         for idt,t in enumerate(T):
#             scoreMatrix[idm,idk,idt] = filterKnn.measure_knn_acc(t,k,L,EQ,ED)