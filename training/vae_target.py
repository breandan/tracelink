from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import gc
import logging
logging.getLogger('tensorflow').disabled = True
import os
os.environ['TF_CPP_MIN_VLOG_LEVEL'] = '3'
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'

from keras.layers import Lambda, Input, Dense
from keras.models import Model
from keras.losses import mse, binary_crossentropy
from keras.utils import plot_model
from keras import backend as K
from keras.models import load_model

from scipy.stats import norm

import random
import numpy as np

import pandas as pd


# reparameterization trick
# instead of sampling from Q, sample epsilon
# z = z_mean + sqrt(var) * epsilon
def sampling(args):
    """Reparameterization trick by sampling from a unit Gaussian.
    # Arguments
        args (tensor): mean and log of variance of Q
    # Returns
        z (tensor): sampled latent vector
    """

    z_mean, z_log_var = args
    batch = K.shape(z_mean)[0]
    dim = K.int_shape(z_mean)[1]
    # by default, random_normal has mean = 0 and std = 1.0
    epsilon = K.random_normal(shape=(batch, dim))
    return z_mean + K.exp(0.5 * z_log_var) * epsilon



def getVAEModel(dataset, original_dim_inputs, intermediate_dim, latent_dim, batch_size, epochs):
    """Creates a VAE model in keras, trains it on a dataset and returns the model
    # Arguments
        dataset: a dataframe contaning the data for training for the VAE, no target variables are needed in a VAE
        original_dim_inputs: the dimension of 1 observations of the input data
        intermediate_dim: the dimension of the intermediate states in the VAE
        batch_size: the batch size for training the model
        latent_dim: the dimension of the latent state in the VAE
        epochs: iterations in training the model
    # Returns
        vae: a fitted keras model of the VAE autoencoder
    """

    # Define train and test set
    x_train = dataset.sample(frac=0.8, random_state=200)
    x_test = dataset.drop(x_train.index)

    original_dim = original_dim_inputs
    x_train = np.reshape(x_train, [-1, original_dim])
    x_test = np.reshape(x_test, [-1, original_dim])

    input_shape = (original_dim,)

    # VAE model; encoder + decoder

    # build encoder model
    inputs = Input(shape=input_shape, name='encoder_input')
    x = Dense(intermediate_dim, activation='relu')(inputs)
    z_mean = Dense(latent_dim, name='z_mean')(x)
    z_log_var = Dense(latent_dim, name='z_log_var')(x)

    # use reparameterization trick to push the sampling out as input
    z = Lambda(sampling, output_shape=(latent_dim,), name='z')([z_mean, z_log_var])

    # instantiate encoder model
    encoder = Model(inputs, [z_mean, z_log_var, z], name='encoder')
    # encoder.summary()

    # build decoder model
    latent_inputs = Input(shape=(latent_dim,), name='z_sampling')
    x = Dense(intermediate_dim, activation='relu')(latent_inputs)
    outputs = Dense(original_dim, activation='sigmoid')(x)

    # instantiate decoder model
    decoder = Model(latent_inputs, outputs, name='decoder')
    # decoder.summary()

    # instantiate VAE model
    outputs = decoder(encoder(inputs)[2])
    vae = Model(inputs, outputs, name='vae_mlp')

    reconstruction_loss = binary_crossentropy(inputs, outputs)

    reconstruction_loss *= original_dim
    kl_loss = 1 + z_log_var - K.square(z_mean) - K.exp(z_log_var)
    kl_loss = K.sum(kl_loss, axis=-1)
    kl_loss *= -0.5

    vae_loss = K.mean(reconstruction_loss + kl_loss)
    vae.add_loss(vae_loss)
    vae.compile(optimizer='adam')

    # vae.summary()
    plot_model(vae, to_file='vae_mlp.png', show_shapes=True)
    print("TRAIN")
    # train the autoencoder
    vae.fit(x_train, epochs=epochs, batch_size=batch_size, validation_data=(x_test, None), verbose=0)

    #save the model
    vae.save('vae_model_targets.h5')
    encoder.save('vae_encoder_model_targets.h5')

    return vae

def encodeVAE(dataset,original_dim_inputs,batch_size):
    """Uses a created VAE to make predictions on new data
        # Arguments
            dataset: a dataframe contaning the data for training for the VAE, no target variables are needed in a VAE
            original_dim_inputs: the dimension of 1 observations of the input data
            batch_size: the batch size for training the model
        #Returns
            dataset_encoded: the dataset encoded with the vae
    """

    encoder = load_model('vae_encoder_model_targets.h5')
    original_dim = original_dim_inputs
    dataset = np.reshape(dataset, [-1, original_dim])

    dataset_encoded = encoder.predict(dataset, batch_size=batch_size)

    return dataset_encoded



def vaeGenerator(dataset, original_dim_inputs, intermediate_dim, latent_dim, batch_size, epochs,samples_count):
    """Creates a VAE model in keras, trains it on a dataset and returns the model
    # Arguments
        dataset: a dataframe contaning the data for training for the VAE, no target variables are needed in a VAE
        original_dim_inputs: the dimension of 1 observations of the input data
        intermediate_dim: the dimension of the intermediate states in the VAE
        batch_size: the batch size for training the model
        latent_dim: the dimension of the latent state in the VAE
        epochs: iterations in training the model
    # Returns
        dataset_encoded: a fitted keras model of the VAE autoencoder
    """

    # Define train and test set
    x_train = dataset

    #.sample(frac=0.8, random_state=200)
    #x_test = dataset.drop(x_train.index)

    original_dim = original_dim_inputs
    x_train = np.reshape(x_train, [-1, original_dim])
    # print(x_train)
    #x_test = np.reshape(x_test, [-1, original_dim])

    input_shape = (original_dim,)

    # VAE model; encoder + decoder

    x = Input(batch_shape=(batch_size, original_dim))
    h = Dense(intermediate_dim, activation='relu')(x)
    z_mean = Dense(latent_dim)(h)
    z_log_sigma = Dense(latent_dim)(h)

    def samplingForGen(args):
        z_mean, z_log_sigma = args
        epsilon = K.random_normal(shape=(batch_size, latent_dim),
                                  mean=0., stddev=1.0)
        return z_mean + K.exp(z_log_sigma) * epsilon

    z = Lambda(samplingForGen, output_shape=(latent_dim,))([z_mean, z_log_sigma])

    decoder_h = Dense(intermediate_dim, activation='relu')
    decoder_mean = Dense(original_dim, activation='sigmoid')
    h_decoded = decoder_h(z)
    x_decoded_mean = decoder_mean(h_decoded)

    # end-to-end autoencoder
    vae = Model(x, x_decoded_mean)

    # encoder, from inputs to latent space
    encoder = Model(x, z_mean)

    # generator, from latent space to reconstructed inputs
    decoder_input = Input(shape=(latent_dim,))
    _h_decoded = decoder_h(decoder_input)
    _x_decoded_mean = decoder_mean(_h_decoded)
    generator = Model(decoder_input, _x_decoded_mean)

    reconstruction_loss = binary_crossentropy(x, x_decoded_mean)

    reconstruction_loss *= original_dim
    kl_loss = 1 + z_log_sigma - K.square(z_mean) - K.exp(z_log_sigma)
    kl_loss = K.sum(kl_loss, axis=-1)
    kl_loss *= -0.5

    vae_loss = K.mean(reconstruction_loss + kl_loss)
    vae.add_loss(vae_loss)

    vae.compile(optimizer='rmsprop')

    # vae.summary()

    vae.fit(x_train,
            shuffle=True,
            epochs=epochs,
            batch_size=batch_size,
            verbose=0)

    # linearly spaced coordinates on the unit square were transformed through the inverse CDF (ppf) of the Gaussian
    # to produce values of the latent variables z, since the prior of the latent space is Gaussian

    samples=np.zeros((samples_count, latent_dim))
    output_gen=np.zeros((samples_count,original_dim_inputs))

    for i in range(samples_count):
         for j in range(latent_dim):
             samples[i,j] = norm.ppf(random.uniform(0.05, 0.95))


    for i in range(samples_count):
        z_sample = np.array(samples[i,:])
        z_sample=z_sample.reshape((1,latent_dim))
        x_decoded = generator.predict(z_sample)
        output_gen[i,] = x_decoded

    K.clear_session()
    vae = None
    del vae
    gc.collect()
    return output_gen


dataset=pd.read_csv("data3/target_context.csv")
dataset.drop(dataset.columns[[0]], axis=1, inplace=True)
print(dataset)
print(dataset.shape)

#dataset=dataset[0:10]

#dataset=dataset.head(100)
#print(dataset.shape)

getVAEModel(dataset, 768, 350, 50, 20, 10)

encoded_data=encodeVAE(dataset,768,100)
print(encoded_data)

encoded_data.to_csv(saveNames("dataset_target_context_encoded.csv")
