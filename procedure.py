from cvxopt.glpk import ilp
import numpy as np
from cvxopt import matrix

def solve (distances, demand, cabs): # new demand, old demand
    n = 0
    if (len(cabs) > len(demand)): n = len(cabs) 
    else: n = len(demand)
    cost = [[n*n for i in range(n)] for j in range(n)] # array filled with huge costs
    for c_id, c_frm, c_to in cabs:
        for d_id, d_frm, d_to in demand:
            cost[c_id][d_id] = distances[c_to][d_frm]
    
    c=matrix(cost, tc='d')
    # constraints
    arr = np.zeros((n*2, n*n)) 
    for i in range(0,n):
        for j in range(0,n): 
            arr[i][n*i+j]=1.0
            arr[n+i][n*j+i]=1.0
    a=matrix(arr, tc='d') 
    g=matrix([ [0 for x in range(n*n)] ], tc='d')
    b=matrix(1*np.ones(n*2))
    h=matrix(0*np.ones(1))
    I=set(range(n*n))
    B=set(range(n*n))
    (status,x)=ilp(c,g.T,h,a,b,I,B)
    #suma = sum(c.T*x)
    return x

################################################################
n_stands=10 # number of stands
n_cabs=3
n_cust=4
dist = np.zeros((n_stands,n_stands)) # distances
# calculate distances
for i in range(0,n_stands):
    for j in range(i,n_stands):
        dist[j][i]=j-i # simplification of distance - stop9 is closer to stop7 than to stop1
        dist[i][j]=dist[j][i] 
new_demand = []
# ID, from, to
new_demand.append((0, 0,2))  # indices in Python start with 0
new_demand.append((1, 0,5))
new_demand.append((2, 3,1))
new_demand.append((n_cust-1, 5,1))
current_trips = []
# ID, from, to; from not used at that moment
current_trips.append((0, 3,3))
current_trips.append((1, 3,1))
current_trips.append((n_cabs-1, 0,5))

x = solve(dist, new_demand, current_trips)
for dem in range(0,n_cabs): # three cabs
   for trip in range(0,n_cust): # four customers
       if x[n_cust*dem+trip]==1:
          print ("cab %d takes customer %d" % (dem, trip))