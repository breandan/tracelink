"""
Created by Pierre Orhan 25/11/2019

Some simple regression models acting as baselines.
"""

from sklearn.manifold import TSNE
from sklearn.decomposition import PCA
from sklearn.cross_decomposition import CCA
from sklearn.linear_model import RidgeCV
# A model that concatenate multiple other model:
class chain():
    def __init__(self,models,args):
        self.models = []
        for m,arg in zip(models,args):
            if type(arg)==list:
                self.models +=[m(*arg)]
            else:
                self.models +=[m(arg)]
    def fit(self,query,target):
        a,b = query,target
        for m in self.models:
            a,b = m(a,b)
        return a,b
    def __str__(self):
        model_str = [str(m) for m in self.models]
        name = "Chain["
        for s in model_str:
            name+=s
        name+="]"
        return name

#raw dimension reductions model
class tsneModel():
    def __init__(self,num_dimensions):
        self.tsne = TSNE(n_components=num_dimensions, random_state=0)
    def fit(self,query,target):
        target_vectors = self.tsne.fit_transform(target)
        query_vectors = self.tsne.fit_transform(query)
        return query_vectors,target_vectors
    def __str__(self):
        return "tsne("+str(self.tsne.n_components)+")"
class ccaModel():
    def __init__(self,num_dimensions):
        self.cca = CCA(n_components=num_dimensions)
    def fit(self,query,target):
        self.cca.fit(query,target)
        query_vectors,target_vectors = self.cca.transform(query,target)
        return query_vectors,target_vectors
    def __str__(self):
        return "cca("+str(self.cca.n_components)+")"
class pcaModel():
    def __init__(self,num_dimensions):
        self.pca = PCA(n_components=num_dimensions)
    def fit(self,query,target):
        self.pca.fit(query,target)
        query_vectors = self.pca.transform(query)
        target_vectors = self.pca.transform(target)
        return query_vectors,target_vectors
    def __str__(self):
        return "pca("+str(self.pca.n_components)+")"

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
    def __str__(self):
        return self.model.__str__()

from sklearn.gaussian_process import GaussianProcessRegressor
from sklearn.gaussian_process.kernels import RBF
class gpRegression():
    def __init__(self):
        self.gpRegressor = GaussianProcessRegressor(RBF(),random_state=0) #todo: hyperparameter tuning over the kernel
    def fit(self,query,target):
        self.gpRegressor.fit(query,target)
        print("Gaussian process regressor training loss is",self.gpRegressor.score(query,target))
        return self.gpRegressor.predict(query),target
    def __str__(self):
        return self.gpRegressor.__str__()