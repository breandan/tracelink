import torch
from transformers import BertTokenizer, BertModel
import logging
import pandas as pd
import numpy as np
import os
from tqdm  import  tqdm
import matplotlib.pyplot as plt

restrictSize = 1000
data = pd.read_csv(os.path.join("data3","exact_hits.csv"),sep="\t",nrows=restrictSize)
target_link_data = data.values[:,3]
embeddings = [pd.read_csv(os.path.join("data3","link_text.csv")).values[:,1:],
              pd.read_csv(os.path.join("data3","link_context.csv")).values[:,1:],
              pd.read_csv(os.path.join("data3","target_context.csv")).values[:,1:]]

from sklearn.decomposition import PCA
from sklearn.cross_decomposition import CCA
from sklearn.manifold import TSNE

pca = PCA(n_components=2)
pca.fit(embeddings[1],embeddings[2])
X_pca = pca.transform(embeddings[1])
Y_pca = pca.transform(embeddings[2])
plt.scatter(X_pca[:,0],X_pca[:,1],c="blue",label="link_context")
plt.scatter(Y_pca[:,0],Y_pca[:,1],c="orange",label="doc_target",marker="x")
plt.legend()
plt.show()

tsne = TSNE(n_components=2)
tsne.fit(embeddings[1],embeddings[2])
X_tsne= tsne.fit_transform(embeddings[1])
Y_tsne =  tsne.fit_transform(embeddings[2])
plt.scatter(X_tsne[:,0],X_tsne[:,1],c="blue",label="link_context")
plt.scatter(Y_tsne[:,0],Y_tsne[:,1],c="orange",label="doc_target",marker="x")
plt.legend()
plt.show()


cca = CCA(n_components=2)
cca.fit(embeddings[1],embeddings[2])
X_c,Y_c = cca.transform(embeddings[1],embeddings[2])


plt.scatter(X_c[:,0],X_c[:,1],c="blue",label="link_context")
plt.scatter(Y_c[:,0],Y_c[:,1],c="orange",label="doc_target",marker="x")
plt.legend()
plt.show()

from matplotlib.cm import get_cmap
cm = get_cmap('hsv')
#Now we cluster by target database, just to observe if it is reflected:
target_names = np.array([str(c).split(".tgz!")[0] for c in target_link_data])
target_names_unique = np.unique(target_names)[:-4]
hist = []
for idx,base in enumerate(target_names_unique):
    hist+= [sum(np.where(target_names==base,1,0))]
print(hist)
plt.bar(range(0,len(hist)),np.log(hist),color=cm(np.arange(0,len(hist))/len(hist)),width=0.8)
plt.xticks(range(0,len(hist)), target_names_unique, rotation='vertical')
plt.show()


from scipy.spatial import cKDTree
#Let us analyze the distance in the original space:
Z = embeddings[0]
X = embeddings[1]
Y = embeddings[2]

distance_bertSpace = np.sum(np.square(X-Y),axis=1)
distance_bertSpace_link_text = np.sum(np.square(Z-Y),axis=1)
mean_distance_xToY = np.zeros(X.shape[0])
for idx in tqdm(range(0,X.shape[0])):
    mean_distance_xToY[idx] = np.mean(np.sum(np.square(X[idx]-Y),axis=1))
hist_distance_bertSpace_link_text = np.histogram(distance_bertSpace_link_text,bins=100)
hist_distance_bertSpace = np.histogram(distance_bertSpace,bins=100)
hist_mean_distance_xToY = np.histogram(mean_distance_xToY,bins=100)
plt.bar(hist_distance_bertSpace[1][:-1],hist_distance_bertSpace[0],color="black",label="link context to target")
plt.bar(hist_distance_bertSpace_link_text[1][:-1],hist_distance_bertSpace_link_text[0],color="orange",label="link text to target")
plt.bar(hist_mean_distance_xToY[1][:-1],hist_mean_distance_xToY[0],color="blue",label="mean link context to any target")
plt.xlabel("L2 distance, 100 bins")
plt.ylabel("count")
plt.legend()
plt.show()

distance_CCASpace = np.sum(np.square(X_c-Y_c),axis=1)
mean_distance_xToY = np.zeros(X.shape[0])
for idx in tqdm(range(0,X_c.shape[0])):
    mean_distance_xToY[idx] = np.mean(np.sum(np.square(X_c[idx]-Y_c),axis=1))
hist_distance_CCA = np.histogram(np.log(distance_CCASpace),bins=100)
hist_mean_distance_xToY = np.histogram(np.log(mean_distance_xToY),bins=100)
plt.bar(hist_distance_CCA[1][:-1],hist_distance_CCA[0],color="black",label="link context to target")
plt.bar(hist_mean_distance_xToY[1][:-1],hist_mean_distance_xToY[0],color="blue",label="mean link context to any target")
plt.xlabel("L2 distance, 100 bins, CCA space")
plt.ylabel("count")
plt.legend()
plt.show()



#Let us compute the k-accuracy for k in [1,5,10,50]
Y_tree = cKDTree(Y_c)
k_values = [1,5,10,50]
scores = []
for k in k_values:
    queryResultd,queryResultIdx = Y_tree.query(X_c,k)
    indexRange = np.column_stack([np.arange(0,X_c.shape[0]) for _ in range(0,k)])
    if k>1:
        boolean = (queryResultIdx==indexRange)
        scores += [np.sum(np.sum(boolean,axis=1))/X_c.shape[0]]
    else:
        boolean = queryResultIdx==np.squeeze(indexRange)
        scores += [np.sum(boolean)/X_c.shape[0]]
plt.bar(range(1,5),scores)
plt.xticks(range(1,5),k_values)
plt.xlabel("k-neighbor selected")
plt.ylabel("accuracy for CCA 2-dimension")
plt.show()

#Same but for the bert embedding dataset...
X = embeddings[1]
Y = embeddings[2]
Y_tree_bert = cKDTree(Y)
k_values = [1,5,10,50]
scores = []
for k in k_values:
    queryResultd,queryResultIdx = Y_tree_bert.query(X,k)
    indexRange = np.column_stack([np.arange(0,X.shape[0]) for _ in range(0,k)])
    if k>1:
        boolean = (queryResultIdx==indexRange)
        scores += [np.sum(np.sum(boolean,axis=1))/X.shape[0]]
    else:
        boolean = queryResultIdx==np.squeeze(indexRange)
        scores += [np.sum(boolean)/X.shape[0]]
plt.bar(range(1,5),scores)
plt.xticks(range(1,5),k_values)
plt.xlabel("k-neighbor selected")
plt.ylabel("accuracy for knn in bert spae")
plt.show()

import torch
from torch import nn

def train(inputX,inputY,text):
    model = nn.Sequential(
        nn.Linear(2,100,bias=True),
        nn.Tanh(),
        nn.Linear(100,100),
        nn.Tanh(),
        nn.Linear(100,100),
        nn.Tanh(),
        nn.Linear(100,2)
    )
    model.to("cuda")
    model = model.float()
    X_c_train = inputX[:-100,:]
    Y_c_train = inputY[:-100,:]
    nb_batches = 10
    X_c_train_batch = np.stack(np.split(X_c_train,nb_batches,axis=0))
    Y_c_train_batch = np.stack(np.split(Y_c_train,nb_batches,axis=0))
    context = torch.tensor(X_c_train_batch,dtype=torch.float32).to("cuda")
    target = torch.tensor(Y_c_train_batch,dtype=torch.float32).to("cuda")
    context_test = torch.tensor(inputX[-100:,:],dtype=torch.float32).to("cuda")
    target_test = torch.tensor(inputY[-100:,:],dtype=torch.float32).to("cuda")
    loss_fn = torch.nn.MSELoss()
    optimizer = torch.optim.SGD(model.parameters(), lr=0.01, momentum=0.9)
    nbEpoch = 1000
    for e in range(nbEpoch):
        dataSet = zip(context,target)
        for input,target2 in dataSet:
            optimizer.zero_grad()
            output = model(input)
            loss = loss_fn(output, target2)
            loss.backward()
            optimizer.step()
        with torch.no_grad():
            output_test = model(context_test)
            print(loss_fn(output_test,target_test))

    def evaluateKNN(X,Y,text):
        Y_tree_bert = cKDTree(Y)
        k_values = [1,5,10,50]
        scores = []
        for k in k_values:
            queryResultd,queryResultIdx = Y_tree_bert.query(X,k)
            indexRange = np.column_stack([np.arange(0,X.shape[0]) for _ in range(0,k)])
            if k>1:
                boolean = (queryResultIdx==indexRange)
                scores += [np.sum(np.sum(boolean,axis=1))/X.shape[0]]
            else:
                boolean = queryResultIdx==np.squeeze(indexRange)
                scores += [np.sum(boolean)/X.shape[0]]
        plt.bar(range(1,5),scores)
        plt.xticks(range(1,5),k_values)
        plt.xlabel("k-neighbor selected")
        plt.ylabel(text)
        plt.show()

    X_full = torch.tensor(inputX,dtype=torch.float32).to('cuda')
    Y_full = inputY
    output = model(X_full).detach().cpu()
    evaluateKNN(output,Y_full,"distance in "+str(text)+" space")
    plt.scatter(output[:,0],output[:,1],c="blue",label="link_context")
    plt.scatter(Y_full[:,0],Y_full[:,1],c="orange",label="doc_target",marker="x")
    plt.legend()
    plt.show()

train(X_c,Y_c,text="CCA")
train(X_pca,Y_pca,text="PCA")
train(X_tsne,Y_tsne,text="TSNE")