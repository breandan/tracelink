import pandas as pd
import numpy as np
import matplotlib.pyplot as plt


#This is to get the doc label:
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

#This is to lead the context:
data = pd.read_csv("context_TSNE_reduced.csv").values


from matplotlib.cm import get_cmap

labels_for_text_link_norm = labels_for_text_link/unique_database.shape[0] #normalize
cm = get_cmap('hsv')
fig, ax = plt.subplots()
ax.scatter(data[:,0],data[:,1],c=cm(labels_for_text_link_norm))
plt.show()