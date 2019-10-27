from cvxopt.glpk import ilp
import numpy as np
from cvxopt import matrix

hours = 2
reqs_per_minute=100 
n_stands=50

DROP_TIME = 10
THRESHOLD = 20 # stands - max distance for LCM
MAX_OPT = 200 # max size of model put to solver
n_cabs=400
big_cost=250000
##############################


def calculate_cost (distances, demand, cabs):
    n = 0
    if (len(cabs) > len(demand)): n = len(cabs)  # checking max size for unbalanced scenarios
    else: n = len(demand)
    if n==0: return 0,0
    cst = [[big_cost for i in range(n)] for j in range(n)] # array filled with huge costs - it will be overwritten for most cells below
    c_idx=0
    for c_id, c_frm, c_to in cabs:
        d_idx=0
        for d_id, d_frm, d_to in demand:
            if distances[c_to][d_frm]<DROP_TIME: # take this possibility only if reasonable time to pick-up a customer
                # otherwise big_cost will stay in this cell
                cst[c_idx][d_idx] = distances[c_to][d_frm]
            d_idx = d_idx +1
        c_idx = c_idx+1
   
    return n,cst
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

# filters out elements NOT in range - it maybe 'from', 'to' or 'id' which is checked
def filter_out_old (input, range, element):
    output=[]
    for row in input:
        if row[element] not in range:
            output.append(row)
    return len(output), output

def filter_out (input, allocated, element):
    output=[]
    for i in range (0, len(input)): 
        if i not in allocated: # todo: is this the most efficient way to find out what is NOT allocated ?
            output.append(input[i])
    return len(output), output

#################################################################

# heuristic - lowest cost method
# n: size
# c: cost
def LCM(n, c):
    allocated_supply = []
    allocated_demand = []
    allocated = []
    temp=np.array(c) # copy the cost table
    d=temp.flatten()
    total_cost=0
    for i in range(0,n):  # so many iterations we have in LCM 
        elem = d.argmin(0) # 0 is first axis (there is just one)
        if d[elem] > THRESHOLD:
            break;
        row = int(elem/n) # supply
        col = elem - row*n # demand
        allocated_supply.append(row)
        allocated_demand.append(col)
        allocated.append((row,col))
        if d[elem]<big_cost: # do not sum a dummy cost
            total_cost += d[elem]
        for j in range(0,n): 
            d[n*row+j]=big_cost; # marking row with a high cost
            d[j*n+col]=big_cost; # marking column 
    return total_cost, allocated_supply, allocated_demand, allocated
##############################

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
    demand.append((int(id), int(frm), int(to), int(time), int(wait), -1, "", -1))  
    # three last fields
    # 1) index of cab -> -1 not assigned to a cab
    # 2) possible time of pick up !!! there may be multiple proposals
    # 3) time of pick up
cabs=[]
for idx in range(0, n_cabs):
    cabs.append((idx, int(n_stands/2), int(n_stands/2), -1, 0, -1))  # cabs starts the day somewhere in the middle (/2) of the place
    # last three fields
    # passenger assigned, ID: -1 = no passenger assigned
    # with/without passenger on board: 1/0; used for filtering out cabs that should go to solver
    # time started

f = open("c:\\home\\dell\\simulog2.txt", "w")
f_solv = open("c:\\home\\dell\\simulog_solv2.txt", "w")
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
                f.write("Time %d. Cab %d is free at stand %d\n" % (t, c[0], c[2]))
                #frm=to # "I am free!"
                #c[3]=-1 # no passenger assigned
                #c[4]=0 # no passenger on board; 
                #c[5]=-1 # no time started; maybe useful to count wasted time ?
    # create demand for the solver                
    temp_demand=[]
    for d in demand:
        if d[5]==-1 and t>=d[4]: # not assigned, not dropped (-2) and not eralier than requested
            if t-d[4] > DROP_TIME: # this customer will never be serviced, after 10 minutes of waiting the customer will look for something else
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
    opt=0
    allocated_pairs=[]
    if len(temp_demand)<MAX_OPT and len(temp_supply)<MAX_OPT: # small enough for solver
        opt=1
        nn, x, cost = solve(dist, temp_demand, temp_supply)
        f_solv.write("Time %d: optimal solution of size %d\n" % (t, nn))
    else: # nope, use LCM first to minimize the size
        lcm_demand = temp_demand
        lcm_supply = temp_supply
        nn, cost = calculate_cost (dist, temp_demand, temp_supply)
        f_solv.write("Time %d: greedy preproc applied of size %d\n" % (t, nn))
        lcm, allocated_cabs, allocated_cust, allocated_pairs = LCM(nn, matrix(cost, tc='d').T)
        nb_cust, temp_demand = filter_out(temp_demand, allocated_cust, 0) 
        nb_cabs, temp_supply = filter_out(temp_supply, allocated_cabs, 0) 
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
                                elif abs(temp_supply[s][2] - temp_demand[c][1]) < DROP_TIME:  # we have a 1D world and indexes of stands indicate the distance
                                    # the check above is not needed, we check the distance in 'calculate_cost'
                                    # start the trip without a passenger but to pick up a passenger
                                    cabs[idx]=(cab[0], temp_supply[s][2], temp_demand[c][1], temp_demand[c][0], 0, t)
                                    f.write("Time %d. Customer %d assigned to cab %d, cab is heading towards the customer\n" 
                                            % (t, temp_demand[c][0], temp_supply[s][0]))
                                break
                    # 
                    # sum = sum + dist[supply[s][2]][demand[c][1]]
    # LCM has to be considered too
    for pair in allocated_pairs:
        error = 0
        if lcm_supply[pair[0]][1]==lcm_supply[pair[0]][2]: # a cab with no assignment, no need to check [3]
            # first log the assignment in the trip request
            for d in demand:
                if d[0]==lcm_demand[pair[1]][0]: # 0: ID
                    idx = demand.index(d)
                    if demand[idx][5] > -1: # that should never happen, but it does - a bug in filter_out ?
                        error = 1
                        f.write("Time %d. Error: Customer %d already assigned to cab %d\n" % (t, demand[idx][0], demand[idx][5]))
                    else:
                        demand[idx]=(d[0], d[1], d[2], d[3], d[4], lcm_supply[pair[0]][0], d[6], t)
                    break
            # assign the job to the cab
            if error == 1:
                continue # skip this pair

            for cab in cabs:
                if cab[0]==lcm_supply[pair[0]][0]: # 0: ID
                    idx = cabs.index(cab)
                    if lcm_supply[pair[0]][2]==lcm_demand[pair[1]][1]: # cab is already there
                        cabs[idx]=(cab[0], lcm_demand[pair[1]][1], lcm_demand[pair[1]][2], lcm_demand[pair[1]][0], 1, t)
                        f.write("Time %d. LCM: Customer %d assigned to and picked up by cab %d\n" 
                            % (t, lcm_demand[pair[1]][0], lcm_supply[pair[0]][0]))
                    elif abs(lcm_supply[pair[0]][2] - lcm_demand[pair[1]][1]) < DROP_TIME:
                        # start the trip without passenger
                        cabs[idx]=(cab[0], lcm_supply[pair[0]][2], lcm_demand[pair[1]][1], lcm_demand[pair[1]][0], 0, t)
                        f.write("Time %d. LCM: Customer %d assigned to cab %d, cab is heading towards the customer\n" 
                             % (t, lcm_demand[pair[1]][0], lcm_supply[pair[0]][0]))
                    break

f.write("Demand after:\n")
for d in demand:
    f.write("(%d,%d,%d,%d,%d,%d,%s,%d)):\n" % (d[0],d[1],d[2],d[3],d[4],d[5],d[6],d[7]
    ))
f.write("Supply after:\n")
for s in cabs:
    f.write("(%d,%d,%d,%d,%d,%d)):\n" % (s[0],s[1],s[2],s[3],s[4],s[5]))
f.close()
f_solv.close()