"""
Created by Pierre Orhan on 22/11/2019
Goal: provide a bert and word2vec embedding of a context

Context is a snippet of code or text or both.
BERT is a pretrained model from google
We use huggingface's transformer labrary to easily retrieve Bert model in pytorch.
"""

import torch
from transformers import BertTokenizer, BertModel
import logging
import pandas as pd
import numpy as np
import os
from tqdm  import  tqdm
logging.basicConfig(level=logging.INFO)

### Loading of BERT model
#load pre-trained model tokenizer (vocabulary)
tokenizer = BertTokenizer.from_pretrained("bert-base-uncased")
model = BertModel.from_pretrained('bert-base-uncased')      #load the model
model.eval()     #switch to eval mode (remove things like dropout)
model.to('cuda')    #Put the model on the gpu:

#Restrict for testing:
restrictSize = 1000
###Loading of the Data:
data = pd.read_csv("data3\exact_hits.csv",sep="\t",nrows=restrictSize)
print(data.columns)
link_text_data = data.values[:restrictSize,0]
link_context_data = data.values[:restrictSize,1]
target_context_data = data.values[:restrictSize,2]
target_link_data = data.values[:restrictSize,4]

# Note: Bert max input sequence is 512 we can get it through:
max_model_input_size = tokenizer.max_model_input_sizes['bert-base-uncased']
def tokenize(data):
    data_token=[]
    for c in tqdm(data):# this step takes about 1 minutes of time.... tqdm is for progression bar
        tok = tokenizer.tokenize(str(c))
        data_token += [tok]
    data_tokenID = []
    for c in tqdm(data_token):
        if len(c)>max_model_input_size: #Just cut....
            c = c[:max_model_input_size]
        tok_id = tokenizer.convert_tokens_to_ids(c)
        data_tokenID += [tok_id]
    return data_token,data_tokenID
link_text_token,link_text_tokenID = tokenize(link_text_data)
link_context_token,link_context_tokenID = tokenize(link_context_data)
target_context_token,target_context_tokenID = tokenize(target_context_data)

# Let us embed everything:
# First we must pad the inputs so that we have a tensor (then we can use batched operation).

# To accelerate embedding, we can use batches of input of similar sizes,
# But we then loose the clear organisation of data, this can be circumvent using a dictionnary ==> not so easy to code...
sizes = [len(c) for c in link_text_tokenID]
unique_sizes = np.unique(sizes)
sizeslink_context_tokenID = [len(c) for c in link_context_tokenID]
unique_sizeslink_context_tokenID = np.unique(sizeslink_context_tokenID)
sizestarget_context_tokenID = [len(c) for c in target_context_tokenID]
unique_sizestarget_context_tokenID = np.unique(sizestarget_context_tokenID)

pad_dim = max(unique_sizes)
link_text_tokenID_padded = [c+[0 for _ in range(pad_dim-len(c))] for c in link_text_tokenID]
pad_dim = max(sizeslink_context_tokenID)
link_context_tokenID_padded = [c+[0 for _ in range(pad_dim-len(c))] for c in link_context_tokenID]
pad_dim = max(sizestarget_context_tokenID)
target_context_tokenID_padded = [c+[0 for _ in range(pad_dim-len(c))] for c in target_context_tokenID]

# Now we proceed to BERT embedding
toEmbed = [link_text_tokenID_padded,link_context_tokenID_padded,target_context_tokenID_padded]
saveNames = [os.path.join("data3","link_text.csv"),os.path.join("data3","link_context.csv"),os.path.join("data3","target_context.csv")]
mini_batch_sizes = [20,20,1]
embeddings = []
for idx,source in enumerate(toEmbed):
    text_tensor = torch.tensor(source)
    #Typically here we run out of memory on cuda if trying to do everything in a single batch, so need to separate in mini-batch
    text_outputs = np.empty((0,768))
    mini_batch_size = mini_batch_sizes[idx]
    for j in tqdm(range(0,text_tensor.shape[0],mini_batch_size)): # takes about 40 minutes to run...
        text_tensor_mb = text_tensor[j:min(j+mini_batch_size,text_tensor.shape[0])].to('cuda')
        with torch.no_grad():
            minibatch_outputs = torch.mean(model(text_tensor_mb)[0],dim=1).cpu()
            text_outputs = np.concatenate([text_outputs,minibatch_outputs])
    # save into a csv
    save_link_text_outputs = pd.DataFrame(text_outputs)
    save_link_text_outputs.to_csv(saveNames[idx])
    embeddings += [save_link_text_outputs]



