"""
Created by Pierre Orhan 25/11/2019

Some simple regression models acting as baselines.
"""

from sklearn.manifold import TSNE
from sklearn.decomposition import PCA
from sklearn.cross_decomposition import CCA
from sklearn.model_selection import GridSearchCV,RandomizedSearchCV
import scipy.stats as spst


import matplotlib.pyplot as plt
# A model that concatenate multiple other model:
class chain():
    def __init__(self,models,args):
        self.models = []
        for m,arg in zip(models,args):
            if type(arg)==list:
                self.models +=[m(*arg)]
            elif arg!=None:
                self.models +=[m(arg)]
            else:
                self.models +=[m()]
    def fit(self,query,target):
        a,b = query,target
        for m in self.models:
            a,b = m.fit(a,b)
        return a,b
    def predict(self,query,target):
        a,b = query,target
        for m in self.models:
            a,b = m.predict(a,b)
        return a,b
    def __str__(self):
        model_str = [str(m) for m in self.models]
        name = "Chain["
        for s in model_str:
            name+=s
        name+="]"
        return name
    def getmodel(self):
        return [m.getmodel() for m in self.models]


#raw dimension reductions model
class tsneModel():
    def __init__(self,num_dimensions):
        self.tsne = TSNE(n_components=num_dimensions, random_state=0)
    def fit(self,query,target):
        target_vectors = self.tsne.fit_transform(target)
        query_vectors = self.tsne.fit_transform(query)
        return query_vectors,target_vectors
    def predict(self,query,target):
        return self.fit(query,target) #The problem with TSNE is that it doesn't provide a predict function....
    def __str__(self):
        return "tsne("+str(self.tsne.n_components)+")"
    def getmodel(self):
        return self.tsne
class ccaModel():
    def __init__(self,num_dimensions):
        self.cca = CCA(n_components=num_dimensions) #But n_components will be changed by the  search
        param_distributions = {
            "n_components":np.arange(2,100,20),
            "scale":[False,True],
            "max_iter":[500,1000],
            "tol": [1e-06,1e-05,1e-07]
        }
        self.randomSearch = GridSearchCV(self.cca,param_grid=param_distributions,
                                          cv=3,n_jobs=3)
    def fit(self,query,target):
        self.randomSearch.fit(query,target)
        query_vectors,target_vectors = self.predict(query,target)
        return query_vectors,target_vectors
    def predict(self,query,target):
        query_vectors = self.randomSearch.transform(query)
        target_vectors = self.randomSearch.transform(target)
        return query_vectors,target_vectors
    def __str__(self):
        return "cca("+str(self.cca.n_components)+")"
    def getmodel(self):
        return self.randomSearch
class pcaModel():
    def __init__(self,num_dimensions):
        self.pca = PCA(n_components=num_dimensions)
        param_distributions = {
            "n_components":np.arange(2,100,20),
            "whiten":[False,True],
        }
        self.randomSearch = GridSearchCV(self.pca,param_grid=param_distributions,
                                               cv=3,n_jobs=-1)
    def fit(self,query,target):
        self.randomSearch.fit(query)
        query_vectors = self.randomSearch.transform(query)
        target_vectors = self.randomSearch.transform(target)
        return query_vectors,target_vectors
    def predict(self,query,target):
        query_vectors = self.randomSearch.transform(query)
        target_vectors = self.randomSearch.transform(target)
        return query_vectors,target_vectors
    def __str__(self):
        return "pca("+str(self.pca.n_components)+")"
    def getmodel(self):
        return self.randomSearch

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
        self.num_dimensions = num_dimensions
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
        loss_values = []

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
                loss_values+=[loss_fn(output_test,Y_c_train_batch_tensor[-1])]
        plt.plot(loss_values)
        plt.xlabel("epochs")
        plt.ylabel("loss value")
        plt.show()
        return self.predict(query,target) #the target is not modified here...
    def predict(self,query,target):
        with torch.no_grad():
            X = torch.tensor(query,dtype=torch.float32).to("cuda")
            output_test = self.model(X).cpu()
        return output_test.numpy(),target
    def __str__(self):
        return "NN("+str(self.num_dimensions)+")"

from sklearn.neural_network import  MLPRegressor
from scipy.stats import  randint
class MLPRegression():
    def __init__(self,size):
        self.mlp = MLPRegressor(batch_size=20) #Don't search over batch size or tolerance.
        param_distributions = {
            "hidden_layer_sizes":[tuple([50 for _ in range(size)]),tuple([100 for _ in range(size)]),tuple([1000 for _ in range(size)])],
            "activation":["relu"],
            "alpha":[0.00001,0.0001,0.001,0.01],
            "learning_rate": ["constant","adaptive"],
            "learning_rate_init":[0.0001,0.001],
            "max_iter":[200,1000,1500],
            "beta_1":[0.9,0.8,0.99],
            "beta_2":[0.999,0.9]
        }
        self.size = size
        self.randomSearch = RandomizedSearchCV(self.mlp,param_distributions=param_distributions,n_iter=1,
                                               cv=3,n_jobs=3,verbose=2)
    def fit(self,query,target):
        self.randomSearch.fit(query,target)
        return self.predict(query,target)
    def predict(self,query,target):
        query_vectors = self.randomSearch.predict(query)
        return query_vectors,target
    def __str__(self):
        return "MLP("+str(self.size)+")"
    def getmodel(self):
        return self.randomSearch

from sklearn.gaussian_process import GaussianProcessRegressor
from sklearn.gaussian_process.kernels import RBF,ConstantKernel,Matern,RationalQuadratic
class gpRegression():
    def __init__(self):
        self.gpRegressor = GaussianProcessRegressor(RBF(),random_state=0) #todo: hyperparameter tuning over the kernel
        param_grid = {
            "kernel": [ConstantKernel(),Matern(),RBF(),RationalQuadratic()]
        }
        self.gridsearch = GridSearchCV(self.gpRegressor,param_grid=param_grid,cv=3,n_jobs=3)
    def fit(self,query,target):
        self.gridsearch.fit(query,target)
        return self.predict(query,target)
    def predict(self,query,target):
        return self.gridsearch.predict(query),target
    def __str__(self):
        return "GPR"
    def getmodel(self):
        return self.gridsearch