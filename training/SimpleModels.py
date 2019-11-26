"""
Created by Pierre Orhan 25/11/2019

Some simple regression models acting as baselines.
"""

from sklearn.manifold import TSNE
from sklearn.decomposition import PCA
from sklearn.cross_decomposition import CCA
# A model that concatenate multiple other model:
class chain():
    def __init__(self,models,args):
        self.models = []
        for m,arg in zip(models,args):
            self.models +=[m(args)]
    def fit(self,query,target):
        a,b = query,target
        for m in self.models:
            a,b = m(a,b)
        return a,b

#raw dimension reductions model
class tsneModel():
    def __init__(self,num_dimensions):
        self.tsne = TSNE(n_components=num_dimensions, random_state=0)
    def fit(self,query,target):
        target_vectors = self.tsne.fit_transform(target)
        query_vectors = self.tsne.fit_transform(query)
        return query_vectors,target_vectors
class ccaModel():
    def __init__(self,num_dimensions):
        self.cca = CCA(n_components=num_dimensions)
    def fit(self,query,target):
        self.cca.fit(query,target)
        query_vectors,target_vectors = self.cca.transform(query,target)
        return query_vectors,target_vectors
class pcaModel():
    def __init__(self,num_dimensions):
        self.pca = PCA(n_components=num_dimensions)
    def fit(self,query,target):
        self.pca.fit(query,target)
        query_vectors = self.pca.transform(query)
        target_vectors = self.pca.transform(target)
        return query_vectors,target_vectors

import torch
from torch import nn
import numpy as np
#Now we build a NN model regressor that regress from one embedding space to the other one
#TODO implement D-E for the training of the NN. Especially for the size of the layers.

class nnRegression():
    def __init__(self, num_dimensions, nb_batches, lr=0.01, momentum=0.9, nbEpoch = 100):
        self.model = nn.Sequential(
            nn.Linear(num_dimensions,100,bias=True),
            nn.Tanh(),
            nn.Linear(100,100),
            nn.Tanh(),
            nn.Linear(100,100),
            nn.Tanh(),
            nn.Linear(100,num_dimensions)
        )
        self.model.to("cuda")
        self.model = self.model.float()
        self.nb_batches = nb_batches
        self.lr = lr
        self.momentum = momentum
        self.nbEpoch = nbEpoch

    def fit(self,query,target):
        X_c_train_batch = np.stack(np.split(query,self.nb_batches ,axis=0))
        Y_c_train_batch = np.stack(np.split(target,self.nb_batches ,axis=0))
        X_c_train_batch_tensor = torch.tensor(X_c_train_batch,dtype=torch.float32).to("cuda")
        Y_c_train_batch_tensor = torch.tensor(Y_c_train_batch,dtype=torch.float32).to("cuda")

        loss_fn = torch.nn.MSELoss()
        optimizer = torch.optim.SGD(self.model.parameters(), lr=self.lr , momentum=self.momentum)

        for e in range(self.nbEpoch):
            dataSet = zip(X_c_train_batch_tensor,Y_c_train_batch_tensor)
            for input,target2 in dataSet:
                optimizer.zero_grad()
                output = self.model(input)
                loss = loss_fn(output, target2)
                loss.backward()
                optimizer.step()
            with torch.no_grad(): #just a checking on the last batch.
                output_test = self.model(X_c_train_batch_tensor[-1])
                print(loss_fn(output_test,Y_c_train_batch[-1]))
        return self.model(query),target #the target is not modified here...