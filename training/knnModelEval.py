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

data = pd.read_csv(os.path.join("data3","extra_hits.csv"))
filterKnn = metrics.FilteredKnn(data)

num_dim = 2
#todo build the chains, build the parameter optimizer pipelines
models = [ sm.tsneModel(num_dim),
           sm.ccaModel(num_dim),
           sm.pcaModel(num_dim),
           sm.nnRegression(num_dim,10)
]
#At first, let us evaluate the training results:
L = data.values[:,0]
ks = [1,5,10,25,50]
T = [50,100,200]
scoreMatrix = np.zeros((len(models),len(ks),len(T)))
for idm,m in enumerate(models):
    EQ,ED = m.fit()
    # For different values of k and T, we get the model score:
    for idk,k in enumerate(ks):
        for idt,t in enumerate(T):
            scoreMatrix[idm,idk,idt] = filterKnn.measure_knn_acc(t,k,L,EQ,ED)
