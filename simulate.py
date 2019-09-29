from cvxopt.glpk import ilp
import numpy as np
from cvxopt import matrix

big_cost=250000
hours = 1
reqs_per_minute=1 # 24000/60
n_cabs=6
n_stands=60
##############################

def calculate_cost (distances, demand, cabs):
    n = 0
    if (len(cabs) > len(demand)): n = len(cabs)  # checking max size for unbalanced scenarios
    else: n = len(demand)
    if n==0: return 0,0
    cost = [[big_cost for i in range(n)] for j in range(n)] # array filled with huge costs - it will be overwritten for most cells below
    c_idx=0
    for c_id, c_frm, c_to in cabs:
        d_idx=0
        for d_id, d_frm, d_to in demand:
            cost[c_idx][d_idx] = distances[c_to][d_frm] 
            d_idx = d_idx +1
        c_idx = c_idx+1
   
    return n,cost
##################################################

def solve (distances, demand, cabs): # new demand, old demand
    n, cost = calculate_cost (distances, demand, cabs)
    if n==0: return 0, [], 0
    c = matrix(cost, tc='d')
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
    return n, x, cost
################################################################

dist = np.zeros((n_stands,n_stands)) # distances
# calculate distances
for i in range(0,n_stands):
    for j in range(i,n_stands):
        dist[j][i]=j-i # simplification of distance - stop9 is closer to stop7 than to stop1
        dist[i][j]=dist[j][i] 

demand = []
for t in open('c:\\home\\dell\\taxi_demand.txt').read().split():
    id,frm,to,time,wait = t.strip('()').split(',')
    # time: time index/cycle when this request has to be picked up; wait: 0=now, >4: come in 'n' minutes/simulation cycles
    demand.append((int(id), int(frm),int(to), int(time), int(wait), -1, "", -1))  
    # three last fields
    # 1) index of cab -> -1 not assigned to a cab
    # 2) possible time of pick up !!! there may be multiple proposals
    # 3) time of pick up
cabs=[]
for idx in range(0, n_cabs):
    cabs.append((idx, int(n_cabs/2), int(n_cabs/2), -1, 0, -1))  # cabs starts the day somewhere in the middle (/2) of the place
    # last three fields
    # passenger assigned, ID: -1 = no passenger assigned
    # with/without passenger on board: 1/0; used for filtering out cabs that should go to solver
    # time started

f = open("c:\\Users\\dell\\simulog.txt", "w")
for t in range (0, hours*60):
    # checking if cabs have reached their destinations
    for c in cabs:
        if c[1]!=c[2] and abs(c[1]-c[2]) == t-c[5]: # the cab has gone as long as the distance
            if c[5]==-1:
                f.write("Time %d. Error: Cab %d goes from %d to %d and has no start time\n" % (t, c[0], c[1], c[2]))
            c_idx = cabs.index(c)
            if c[4]==0: # the cab was empty, it was heading a customer assigned in c[3]
                for d in demand:
                    if d[0]==c[3]: # found the customer
                        idx = demand.index(d)
                        demand[idx]=(d[0], d[1], d[2], d[3], d[4], c[0], d[6], t)
                        f.write("Time %d. Customer %d picked up by cab %d\n" % (t, d[0], c[0]))
                        # assign the cab to customer's trip
                        cabs[c_idx] = (c[0], d[1], d[2], d[0], 1, t)
                        break
            else: # a trip has just been completed
                cabs[c_idx] = (c[0], c[2], c[2], -1, 0, -1)
                f.write("Time %d. Cab %d is free\n" % (t, c[0]))
                #frm=to # "I am free!"
                #c[3]=-1 # no passenger assigned
                #c[4]=0 # no passenger on board; 
                #c[5]=-1 # no time started; maybe useful to count wasted time ?
    # create demand for the solver                
    temp_demand=[]
    for d in demand:
        if d[5]==-1 and t>=d[4]: # not assigned and not dropped (-2)
            if t-d[4] > 10: # this customer will never be serviced, after 10 minutes of waiting the customer will look for something else
                idx = demand.index(d)
                demand[idx]=(d[0], d[1], d[2], d[3], d[4], -2, d[6], d[7])
                f.write("Time %d. Customer %d dropped\n" % (t, d[0]))
            else:
                temp_demand.append((d[0],d[1],d[2]))
    temp_supply=[]
    for c in cabs:
        if (c[1]==c[2] and c[3]==-1): # any cab with passenger (maybe stupid to absorb the solver) or standing and not assigned
            # or c[4]==1 without it we are ignoring cabs during a trip
            temp_supply.append((c[0],c[1],c[2]))
    # SOLVE

    nn, x, cost = solve(dist, temp_demand, temp_supply)
    # analyze solution
    for s in range(0,nn): 
        for c in range(0,nn): 
            if x[nn*s+c]==1:
                if cost[s][c] < big_cost: # not a fake assignment (to balance the model)
                    # that is an unnecessary check as we commented out 'or c[4]==1 above
                    if temp_supply[s][1]==temp_supply[s][2]: # a cab with no assignment, no need to check [3]
                        # first log the assignment in the trip request
                        for d in demand:
                            if d[0]==temp_demand[c][0]: # 0: ID
                                idx = demand.index(d)
                                demand[idx]=(d[0], d[1], d[2], d[3], d[4], temp_supply[s][0], d[6], t)
                                break
                        # assign the job to the cab
                        for cab in cabs:
                            if cab[0]==temp_supply[s][0]: # 0: ID
                                idx = cabs.index(cab)
                                if temp_supply[s][2]==temp_demand[c][1]: # cab is already there
                                    cabs[idx]=(cab[0], temp_demand[c][1], temp_demand[c][2], temp_demand[c][0], 1, t)
                                    f.write("Time %d. Customer %d assigned to and picked up by cab %d\n" 
                                        % (t, temp_demand[c][0], temp_supply[s][0]))
                                else:
                                    # start the trip without passenger
                                    cabs[idx]=(cab[0], temp_supply[s][2], temp_demand[c][1], temp_demand[c][0], 0, t)
                                    f.write("Time %d. Customer %d assigned to cab %d, cab is heading towards the customer\n" 
                                        % (t, temp_demand[c][0], temp_supply[s][0]))
                                break
                        # 
                        # sum = sum + dist[supply[s][2]][demand[c][1]]
f.write("Demand after:\n")
for d in demand:
    f.write("(%d,%d,%d,%d,%d,%d,%s,%d)):\n" % (d[0],d[1],d[2],d[3],d[4],d[5],d[6],d[7],))
f.write("Supply after:\n")
for s in cabs:
    f.write("(%d,%d,%d,%d,%d,%d)):\n" % (s[0],s[1],s[2],s[3],s[4],s[5]))
f.close()
