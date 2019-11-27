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
from matplotlib.cm import  get_cmap
from joblib import dump, load

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

#let us first try to split the data randomly
size = doc2vec_link.shape[0]
shuffledIdx = np.arange(size)
np.random.shuffle(shuffledIdx)
testIdx = shuffledIdx[:int(size/10)]
trainIdx = shuffledIdx[int(size/10):]

#10% to test, 90% to train
QTrain = doc2vec_link[trainIdx]
QTest = doc2vec_link[testIdx]
DTrain = doc2vec_target[trainIdx]
DTest = doc2vec_target[testIdx]
#Note that at evaluation time, we will need to merge back DTrain and DTest embedding (EDtrain, EDtest) into one dataset.
#Otherwise it does not make sense to do a query.


#Create the knn filter datastrucutre (includes a word to doc dictionnary)
filterKnn = metrics.FilteredKnn(data)  #the filter should contain test and training example.
#First let us analyze the baselines

T = [1,5,10,25,50,100,200,300]
K = [1,5,10,25,50,100,200]
L = data.values[:,0]
score_count_search = np.zeros((len(K),len(T)))
for idxk,k in enumerate(K):
    for idxt,t in enumerate(T):
        score_count_search[idxk,idxt] = filterKnn.raw_tf_accuracy(t,k,L)
cm = get_cmap("Accent")
for idxk,k in enumerate(K):
    plt.plot(T,score_count_search[idxk,:],c=cm(idxk),label=str(k)+"-accuracy")
plt.legend()
plt.xlabel("size of initial filtering")
plt.ylabel("accuracy of retrieving the target, using raw_count")
plt.show()


num_dim = 2
#todo build the chains, build the parameter optimizer pipelines
models = [
           sm.MLPRegression(3),
           sm.tsneModel(num_dim),
           sm.ccaModel(num_dim),
           sm.pcaModel(num_dim),
           sm.MLPRegression(4),
           sm.MLPRegression(5),
           sm.gpRegression(),
           sm.chain([sm.tsneModel,sm.MLPRegression],[num_dim,3]),
           sm.chain([sm.tsneModel,sm.gpRegression],[num_dim,None]),
           sm.chain([sm.ccaModel,sm.MLPRegression],[num_dim,3]),
           sm.chain([sm.ccaModel,sm.gpRegression],[num_dim,None]),
           sm.chain([sm.pcaModel,sm.MLPRegression],[num_dim,3]),
           sm.chain([sm.pcaModel,sm.gpRegression],[num_dim,None]),
           sm.chain([sm.gpRegression,sm.MLPRegression],[None,3])
]
num_dim = np.arange(2,100,10)
def exploreModelperformance(m,filterKnn,L,Qtrain,Dtrain,Qtest,Dtest):
    T = [1,5,10,25,50,100,200,300]
    K = [1,5,10,25,50,100,200]
    scores = np.zeros((len(K),len(T)))
    EQtrain,EDtrain = m.fit(Qtrain,Dtrain)
    EQtest,EDtest = m.predict(Qtest,Dtest)

    ED = np.zeros((doc2vec_target.shape[0],EDtrain.shape[1]))
    ED[trainIdx] = EDtrain
    ED[testIdx] = EDtest

    for idxk,k in enumerate(K):
        for idxt,t in enumerate(T):
            scores[idxk,idxt] = filterKnn.measure_knn_acc(t,k,L,EQtest,ED)
    cm = get_cmap("Accent")
    for idxk,k in enumerate(K):
        plt.plot(T,scores[idxk,:],c=cm(idxk),label=str(k)+"-accuracy")
    plt.legend()
    plt.xlabel("size of initial filtering")
    plt.ylabel("accuracy for "+str(m))
    plt.show()

def compareModel(models,filterKnn,L,Qtrain,Dtrain,Qtest,Dtest):
    scores = []
    k = 5
    T = 20
    for idx,m in enumerate(models):
        print("===========fitting next model============")
        EQtrain,EDtrain = m.fit(Qtrain,Dtrain)
        EQtest,EDtest = m.predict(Qtest,Dtest)
        ED = np.zeros((doc2vec_target.shape[0],EDtrain.shape[1]))
        ED[trainIdx] = EDtrain
        ED[testIdx] = EDtest
        scores += [filterKnn.measure_knn_acc(t,k,L,EQtest,ED)]
        #Save model parameters
        model = m.getmodel()
        if type(model)==list:
            if not os.path.exists(str(m)):
                os.mkdir(str(m))
            for id,m2 in enumerate(model):
                dump(m2,os.path.join(str(m),str(id)+".joblib"))
        else:
            if not os.path.exists("singlemodel"):
                os.mkdir("singlemodel")
            dump(model,os.path.join("singlemodel",str(m)+".joblib"))
        pd.DataFrame(scores).to_csv("scores.csv") #progressive save
    pd.DataFrame(scores).to_csv("scores.csv")
    """Let us display scores as a bar plot"""
    scores +=[filterKnn.raw_tf_accuracy(t,k,L)]
    labels = [str(m) for m in models]+["raw_count"]
    cm = get_cmap("Accent")
    colors = [cm(idx) for idx in range(len(scores))]
    plt.bar(range(len(scores)),scores,tick_label=labels,color=colors)
    plt.ylabel(str(k)+"-accuracy overt the test set")
    plt.savefig("scores.png")
    plt.show()
    return scores

#At first, let us evaluate the training results:
L = data.values[:,0]
compareModel(models,filterKnn,L,QTrain,DTrain,QTest,DTest)
