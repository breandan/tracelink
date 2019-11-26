"""
Created by Pierre Orhan 25/11/2019
In this document I provide multiple metrics to evaluate how well a query set embedding retrieve the documents

1) Filtered knn neighbor accuracy:
    The algorithm takes into input:
        T: A filter size (int)
        k: The number of neighbor the algorithm can use (int)
        ED: the dataset embedding (ndarray of embeddings)
        EQ: the query set embedding
        L:  the set of string of the link
        D: the dataset of document.
    2 steps, for each query q of embedding Eq:
        1) We first select the T documents {d} with most frequency of L in d
        2) We compute the distance from Eq to filtered documents
"""
from tqdm import tqdm
import heapq
import numpy as np
class FilteredKnn():
    def __init__(self,data):
        self.wordToDoc = {} #For each link: a heap containing elements like (frequency,docIDX)
        for idx,word in tqdm(enumerate(data.values[:,0])):
            self.wordToDoc[word] = (idx,[])
            for idxDoc,target_doc in enumerate(data.values[:,2]):
                heapq.heappush(self.wordToDoc[word][1],(target_doc.count(word),idxDoc,idx))
    def measure_knn_acc(self,T,k,L,EQ,ED):
        score = 0
        for idx,Eq in tqdm(enumerate(EQ)):
            filteredDocIdx = [v[1] for v in self.wordToDoc[L[idx]][1][:T]]
            distances = np.sum(np.square(Eq - ED[filteredDocIdx]),axis=1)
            ranking = np.argsort(distances) #will fail because now distances has less element.... so we need to retrieve the real idx...
            realRanking = filteredDocIdx[ranking] #retrieve the real ranking idx.
            if self.wordToDoc[L[idx]][0] in realRanking[:k]:
                score +=1
        return score/EQ.shape[0]
