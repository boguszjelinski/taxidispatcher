from cvxopt.glpk import ilp
import numpy as np
from cvxopt import matrix

def solve_split (fil, size, distances, demand, cabs): # new demand, old demand
    fil.write ("\n\nsolve_split for %d" % (size))
    if len(demand)==0:
        fil.write("No demand, quiting.")
        return
    if len(cabs)==0:
        fil.write("No supply, quiting.")
        return
    start=0
    split_size = int(size/4)
    while start<size:
        split_demand = []
        nb_cust=0
        split_trips = []   
        nb_cabs=0
        r = range(start, start+ split_size)
        fil.write ("\nThe demand:")
        for d_id, d_frm, d_to in demand:
            f.write("(%d,%d,%d)" % (d_id,d_frm,d_to))
            if d_frm in r:
                split_demand.append((nb_cust, d_frm, d_to))
                nb_cust = nb_cust +1
        fil.write ("\nThe supply:")
        for c_id, c_frm, c_to in cabs:
            f.write("(%d,%d,%d)" % (c_id,c_frm,c_to))
            if c_to in r:
                split_trips.append((nb_cabs,c_frm,c_to))
                nb_cabs = nb_cabs+1
        
        if nb_cust>0 and nb_cabs>0:
            fil.write ("\nrange %d to %d:" % (start, start+split_size))
            fil.write ("\nCustomers:")
            for d_id, d_frm, d_to in split_demand:
                f.write("(%d,%d,%d)" % (d_id,d_frm,d_to))
            fil.write ("\nCabs:")
            for c_id, c_frm, c_to in split_trips:
                f.write("(%d,%d,%d)" % (c_id,c_frm,c_to))

            x4 = solve(distances, split_demand, split_trips)

            for dem in range(0,nb_cabs): # three cabs
                for trip in range(0,nb_cust): # four customers
                    if x4[nb_cust*dem+trip]==1:
                        f.write ("\ncab %d takes customer %d" % (dem, trip))

        start = start + split_size
        break
################################################

def solve (distances, demand, cabs): # new demand, old demand
    n = 0
    if (len(cabs) > len(demand)): n = len(cabs)  # checking max size for unbalanced scenarios
    else: n = len(demand)
    cost = [[n*n for i in range(n)] for j in range(n)] # array filled with huge costs - it will be overwritten for most cells below
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
n_stands=12 # number of stands
n_cabs=0
n_cust=0
dist = np.zeros((n_stands,n_stands)) # distances
# calculate distances
for i in range(0,n_stands):
    for j in range(i,n_stands):
        dist[j][i]=j-i # simplification of distance - stop9 is closer to stop7 than to stop1
        dist[i][j]=dist[j][i] 
f = open("c:\\Users\\dell\\split.txt", "w")
new_demand = []
# ID, from, to; see the PDF file with explaination
f.write ("The total demand:")
dropped =0 # we have to make corrections to the index if some cases are dropped
for i in range(0,n_stands):
    frm = np.random.randint(0,n_stands)
    to = np.random.randint(0,n_stands)
    if (frm != to): #  drop if equals
        new_demand.append((i-dropped, frm, to))  # indices in Python start with 0
        f.write("(%d,%d,%d)" % (i-dropped,frm,to))
        n_cust = n_cust +1
    else:
        dropped = dropped +1
current_trips = []
minus=0
# ID, from, to; from not used at that moment
f.write ("\nThe total supply:")
for i in range(0,n_stands):
    frm = np.random.randint(0,n_stands)
    to = np.random.randint(0,n_stands)
    if (frm != to): #  drop if equals
        current_trips.append((i-dropped, frm, to))  # indices in Python start with 0
        f.write("(%d,%d,%d)" % (i-dropped,frm,to))
        n_cabs = n_cabs +1
    else:
        dropped = dropped +1

x = solve(dist, new_demand, current_trips)
for dem in range(0,n_cabs): # three cabs
   for trip in range(0,n_cust): # four customers
       if x[n_cust*dem+trip]==1:
          f.write ("\ncab %d takes customer %d" % (dem, trip))

solve_split(f, n_stands, dist, new_demand, current_trips)
f.close()