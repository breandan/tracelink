import torch
from torch.autograd import Variable
import torch.nn as nn
import torch.nn.functional as F

N, D_in, H, D_out = 64, 1000, 100, 10

H_2=50

x = torch.randn(N, D_in)
y = torch.randn(N, D_out)

model = torch.nn.Sequential(
    torch.nn.Linear(D_in, H),
    torch.nn.ReLU(),
    torch.nn.Linear(H, H_2),
    torch.nn.ReLU(),
    torch.nn.Linear(H_2, D_out),)
loss_fn = torch.nn.MSELoss(reduction='sum')

learning_rate = 1e-4
optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
for t in range(500):
    # Forward pass: compute predicted y by passing x to the model.
    y_pred = model(x)

    # Compute and print loss.
    loss = loss_fn(y_pred, y)
    print(t, loss.item())
                
    optimizer.zero_grad()

    loss.backward()
    optimizer.step()

torch.save(model,"mapping_model.nn")
