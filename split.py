from cvxopt.glpk import ilp
import numpy as np
from cvxopt import matrix
big_cost=1000

def solve_split (fil, size, distances, demand, cabs): # new demand, old demand
    fil.write ("\n\nsolve_split for %d" % (size))
    if len(demand)==0:
        fil.write("No demand, quiting.")
        return
    if len(cabs)==0:
        fil.write("No supply, quiting.")
        return
    rest_cust=[] # a list of IDs which have not been served
    rest_cabs=[] # which have not been used
    total=0
    start=0
    split_size = int(size/4)
    while start<size:
        split_demand = []
        nb_cust=0
        split_cabs = []   
        nb_cabs=0
        r = range(start, start+ split_size)
        s=0
        for d_id, d_frm, d_to in demand:
            if d_frm in r:
                split_demand.append((d_id, d_frm, d_to))
                nb_cust = nb_cust +1
        for c_id, c_frm, c_to in cabs:
            if c_to in r:
                split_cabs.append((c_id,c_frm,c_to))
                nb_cabs = nb_cabs+1
        
        if nb_cust>0 and nb_cabs>0:
            fil.write ("\n\nRange %d to %d:" % (start, start+split_size))
            fil.write ("\nCustomers:")
            for d_id, d_frm, d_to in split_demand:
                f.write("(%d,%d,%d)" % (d_id,d_frm,d_to))
            fil.write ("\nCabs:")
            for c_id, c_frm, c_to in split_cabs:
                f.write("(%d,%d,%d)" % (c_id,c_frm,c_to))

            x4, c = solve(distances, split_demand, split_cabs)
            if (nb_cabs > nb_cust): nb = nb_cabs  # checking max size for unbalanced scenarios
            else: nb = nb_cust
            
            for taxi in range(0,nb): # three cabs
                for trip in range(0,nb): # four customers
                    if x4[nb * taxi + trip]==1:
                        if c[taxi][trip] == big_cost: # big cost means customer not served
                            if nb_cabs > nb_cust:
                                rest_cabs.append(split_cabs[taxi][0])
                            else: 
                                rest_cust.append(split_demand[trip][0])
                        else:
                            f.write ("\ncab %d takes customer %d" % (split_cabs[taxi][0], split_demand[trip][0]))
                            s = s + dist[split_cabs[taxi][2]][split_demand[trip][1]]
            f.write ("\nSum: %d " % (s) )
        elif nb_cust>0:
            for d_id, d_frm, d_to in split_demand:
                rest_cust.append(d_id)
        else:
            for c_id, c_frm, c_to in split_cabs:
                rest_cabs.append(c_id)
        start = start + split_size
        total = total +s

    # now solve customers without a cab so far
    fil.write ("\n\nRest:")
    rest_demand =[]
    rest_supply =[]
    nb_cust=0
    nb_cabs=0
    fil.write ("\nCustomers:")
    for d_id, d_frm, d_to in demand:
        if d_id in rest_cust:
            rest_demand.append((d_id, d_frm, d_to))
            f.write("(%d,%d,%d)" % (d_id,d_frm,d_to))
            nb_cust = nb_cust +1
    fil.write ("\nCabs:")
    for c_id, c_frm, c_to in cabs:
        if c_id in rest_cabs:
            rest_supply.append((c_id,c_frm,c_to))
            f.write("(%d,%d,%d)" % (c_id,c_frm,c_to))
            nb_cabs = nb_cabs +1

    x5, c_table = solve(distances, rest_demand, rest_supply)

    if (nb_cabs > nb_cust): 
        nn = nb_cabs  # checking max size for unbalanced scenarios
    else: 
        nn = nb_cust
    
    for taxi in range(0,nn): 
        for trip in range(0,nn): 
            if x5[nn*taxi+trip]==1:
                if c_table[taxi][trip] < big_cost:
                    f.write ("\ncab %d takes customer %d" % (rest_supply[taxi][0], rest_demand[trip][0])) # [0] is ID
                    total = total + dist[rest_supply[taxi][2]][rest_demand[trip][1]]
    f.write("\nTotal cost: %d" % (total))
################################################

def solve (distances, demand, cabs): # new demand, old demand
    n = 0
    if (len(cabs) > len(demand)): n = len(cabs)  # checking max size for unbalanced scenarios
    else: n = len(demand)
    cost = [[big_cost for i in range(n)] for j in range(n)] # array filled with huge costs - it will be overwritten for most cells below
    c_idx=0
    for c_id, c_frm, c_to in cabs:
        d_idx=0
        for d_id, d_frm, d_to in demand:
            cost[c_idx][d_idx] = distances[c_to][d_frm] 
            d_idx = d_idx +1
        c_idx = c_idx+1
    
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
    return x, cost

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
        new_demand.append((n_cust, frm, to))  # indices in Python start with 0
        f.write("(%d,%d,%d)" % (n_cust,frm,to))
        n_cust = n_cust +1

current_trips = []
# ID, from, to; from not used at that moment
f.write ("\nThe total supply:")
for i in range(0,n_stands):
    frm = np.random.randint(0,n_stands)
    to = np.random.randint(0,n_stands)
    if (frm != to): #  drop if equals
        current_trips.append((n_cabs, frm, to))  # indices in Python start with 0
        f.write("(%d,%d,%d)" % (n_cabs,frm,to))
        n_cabs = n_cabs +1

x, cost_table = solve(dist, new_demand, current_trips)

if (n_cabs > n_cust): 
    nn = n_cabs  # checking max size for unbalanced scenarios
else: 
    nn = n_cust
res=0
for taxi in range(0,nn): 
    for trip in range(0,nn): 
        if x[nn*taxi+trip]==1:
            if cost_table[taxi][trip] < big_cost:
                f.write ("\ncab %d takes customer %d" % (current_trips[taxi][0], new_demand[trip][0])) # [0] is ID
                res = res + dist[current_trips[taxi][2]][new_demand[trip][1]]

f.write ("\nSum: %d " % (res) )

solve_split(f, n_stands, dist, new_demand, current_trips)
f.close()