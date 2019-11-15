"""
Created by Pierre Orhan on 12/11/2019
Goal: provide a bert embedding of a context
Context is a snippet of code or text or both.
BERT is a pretrained model from google
We use huggingface's transformer labrary to easily retrieve Bert model in pytorch.
"""

import torch
from transformers import BertTokenizer, BertModel
import logging
import pandas as pd
import numpy as np
from tqdm  import  tqdm
logging.basicConfig(level=logging.INFO)

#load pre-trained model tokenizer (vocabulary)
tokenizer = BertTokenizer.from_pretrained("bert-base-uncased")

#test
text= "[CLS] Who was Jim Henson ? [SEP] Jim Henson was a puppeteer [SEP]"
tokenized_text = tokenizer.tokenize(text) #separate the text into multiple token
print(tokenized_text)
indexed_tokens = tokenizer.convert_tokens_to_ids(tokenized_text) #from token to vocabulary index!
print(indexed_tokens)
# Define sentence A and B indices associated to 1st and 2nd sentences (see paper)
segments_ids = [0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1]

model = BertModel.from_pretrained('bert-base-uncased')  #load the model
model.eval()     #switch to eval mode (remove things like dropout)
#Convert input to pytorch tensors
tokens_tensor = torch.tensor([indexed_tokens])
segments_tensors = torch.tensor([segments_ids])
#Put everything on the gpu:
tokens_tensor = tokens_tensor.to('cuda')
segments_tensors = segments_tensors.to('cuda')
model.to('cuda')

with torch.no_grad():
    outputs = model(tokens_tensor, token_type_ids = segments_tensors)
    # use model.__doc__ to get more info
    # returns: a tuple; last_hidden_state: (1,n,768): embedding of the n words in the n-dimensional space. (batch_size,seqlen,hiddensize)
    #                   pooler_output: (1,768) ? Last layer hidden-state of first token (classification token)
    #                                            is further processed by Linear layer and Tanh.
    #                                             Linear layer weights trained from next sentence prediction (classification)
    #                     ==> not a good summary! better averaging or pooling the sequence of hidden-states for the whole input sequence. <== interesting for us

# Experiments notes:
# When not using token_type_ids or not: there is some variations in the encoding, which might be quite important!

# Experiment 2: I try to build a t-sne embedding on bert on the input, and may be see if it can cluster some API for example.
link_text = pd.read_csv("links_with_context.csv",usecols=[0])
link_text_data = link_text.values
link_text_data = link_text_data.reshape(link_text_data.shape[0])
link_text_data_nostringmark = [c.replace("\"","") for c in link_text_data]
link_text_data_nostringmark_tokeninit = ["[CLS] "+c+" [SEP]" for c in link_text_data_nostringmark] #add separator used in BERT
link_text_token=[]
for c in tqdm(link_text_data_nostringmark_tokeninit):# this step takes about 1 minutes of time.... tqdm is for progression bar
    link_text_token += [tokenizer.tokenize(c)]
link_text_tokenID =  [tokenizer.convert_tokens_to_ids(c) for c in link_text_token]

# Let us embed everything:
# First we must pad the inputs so that we have a tensor (then we can use batched operation)
# Note: Bert max input sequence is 512 we can get it through:
max_model_input_size = tokenizer.max_model_input_sizes['bert-base-uncased']
pad_dim = 0
for c in link_text_tokenID:
    if (len(c)>pad_dim):
        pad_dim = len(c)
assert pad_dim<max_model_input_size
link_text_tokenID_padded = [c+[0 for _ in range(pad_dim-len(c))] for c in link_text_tokenID]
print("pad_dim for link_text is ",pad_dim)

link_text_tensor = torch.tensor(link_text_tokenID_padded)
#Typically here we run out of memory on cuda if trying to do everything in a single batch, so need to separate in mini-batch
link_text_outputs = np.empty((0,768))
mini_batch_size = 200
for j in tqdm(range(0,link_text_tensor.shape[0],mini_batch_size)): # takes about 40 minutes to run...
    link_text_tensor_mb = link_text_tensor[j:min(j+mini_batch_size,link_text_tensor.shape[0])].to('cuda')
    with torch.no_grad():
        link_text_minibatch_outputs = torch.mean(model(link_text_tensor_mb)[0],dim=1).cpu()
        link_text_outputs = np.concatenate([link_text_outputs,link_text_minibatch_outputs])
# save into a csv
save_link_text_outputs = pd.DataFrame(link_text_outputs)
save_link_text_outputs.to_csv("link_text_embeding.csv")

from sklearn.decomposition import PCA
from sklearn.manifold import TSNE

PCA_tool = PCA(n_components=50) # we will keep the 50 best component and then execute a t-sne on this
PCA_reduced = PCA_tool.fit(link_text_outputs)
save_PCA_reduced = pd.DataFrame(PCA_reduced)
save_PCA_reduced.to_csv("text_link_PCA_reduced.csv")
TSNE_tool = TSNE()
TSNE_reduced = TSNE_tool.fit_transform(PCA_reduced)
save_TSNE_reduced= pd.DataFrame(TSNE_reduced)
save_TSNE_reduced.to_csv("text_link_TSNE_reduced.csv")

import matplotlib.pyplot as plt
plt.scatter(TSNE_reduced[:,0],TSNE_reduced[:,1])
plt.show()

#Now we look at where the text came from:
# Processing steps to be able to tackle the problem of , between """ """:
# 1) replace all """, by """*
# 2) replace all , by [COM], just change this for the header
# 3) replace all """* by """,
# 4) replace all """, """, by """, """   (this comes form the fact that in some places in the document there is """* ... """"
# ==> boring manual stuff, but could not figure how to do it with pandas.
inputDoc = pd.read_csv("links_with_context.csv",usecols=[2])
inputDoc_data = inputDoc.values
inputDoc_data = inputDoc_data.reshape(inputDoc_data.shape[0])
inputDoc_data = [str(c).replace("\"","").replace("[COM]",",").replace(" ","") for c in inputDoc_data]

database_data = np.array([c.split("/")[0] for c in inputDoc_data])
unique_database = np.unique(database_data)
# For every text_link, we assign to it a label that corresponds to its unique_database
labels_for_text_link = np.zeros(len(inputDoc_data))
for idx,base in  enumerate(unique_database):
    labels_for_text_link = np.where(database_data==base,idx,labels_for_text_link)

from matplotlib.cm import get_cmap

labels_for_text_link_norm = labels_for_text_link/unique_database.shape[0] #normalize
cm = get_cmap('hsv')
fig, ax = plt.subplots()
ax.scatter(TSNE_reduced[:,0],TSNE_reduced[:,1],c=cm(labels_for_text_link_norm))
plt.show()


#Merge the linked text (target) with its context:
context = pd.read_csv("links_with_context.csv",usecols=[1])
context_data = context.values
context_data=context_data.reshape(context_data.shape[0])
context_data_nostringmark = [ c.replace("\"\"\"","") for c in context_data]
nbrLNK = ["<<LNK>>" in c1 for c1 in context_data_nostringmark]
print("There is <<LNK>> in ",sum(nbrLNK),"out of",len(context_data_nostringmark))
context_data_addedLNK = [c.replace("<<LNK>>",c2) for c,c2 in zip(context_data_nostringmark,link_text_data_nostringmark)]
context_token=[]
for c in tqdm(context_data_addedLNK):# this step takes about 1 minutes of time.... tqdm is for progression bar
    context_token += [tokenizer.tokenize(c)]
context_tokenID =  [tokenizer.convert_tokens_to_ids(c) for c in context_token]

# Let us embed everything:
# First we must pad the inputs so that we have a tensor (then we can use batched operation)
# Note: Bert max input sequence is 512 we can get it through:
max_model_input_size = tokenizer.max_model_input_sizes['bert-base-uncased']
pad_dim = 0
for c in context_tokenID:
    if (len(c)>pad_dim):
        pad_dim = len(c)
assert pad_dim<max_model_input_size
context_tokenID_padded = [c+[0 for _ in range(pad_dim-len(c))] for c in context_tokenID]
print("pad_dim for link_text is ",pad_dim)

context_tensor = torch.tensor(context_tokenID_padded)
#Typically here we run out of memory on cuda if trying to do everything in a single batch, so need to separate in mini-batch
context_outputs = np.empty((0,768))
mini_batch_size = 10
for j in tqdm(range(0,context_tensor.shape[0],mini_batch_size)): # takes about 40 minutes to run...
    context_tensor_mb = context_tensor[j:min(j+mini_batch_size,context_tensor.shape[0])].to('cuda')
    with torch.no_grad():
        context_minibatch_outputs = torch.mean(model(context_tensor_mb)[0],dim=1).cpu()
        context_outputs = np.concatenate([context_outputs,context_minibatch_outputs])
# save into a csv
context_tensor = torch.tensor(context_tokenID_padded)
#Typically here we run out of memory on cuda if trying to do everything in a single batch, so need to separate in mini-batch
context_outputs = np.empty((0,768))
mini_batch_size = 10
for j in tqdm(range(0,context_tensor.shape[0],mini_batch_size)): # takes about 40 minutes to run...
    context_tensor_mb = context_tensor[j:min(j+mini_batch_size,context_tensor.shape[0])].to('cuda')
    with torch.no_grad():
        context_minibatch_outputs = torch.mean(model(context_tensor_mb)[0],dim=1).cpu()
        context_outputs = np.concatenate([context_outputs,context_minibatch_outputs])

PCA_tool_context = PCA(n_components=50) # we will keep the 50 best component and then execute a t-sne on this
PCA_reduced_context = PCA_tool_context.fit_transform(context_outputs)
save_PCA_reduced_context = pd.DataFrame(PCA_reduced_context)
save_PCA_reduced_context.to_csv("context_PCA_reduced.csv")
TSNE_tool_context = TSNE()
TSNE_reduced_context = TSNE_tool_context.fit_transform(PCA_reduced_context)
save_TSNE_reduced_context= pd.DataFrame(TSNE_reduced_context)
save_TSNE_reduced_context.to_csv("context_TSNE_reduced.csv")