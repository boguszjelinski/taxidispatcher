from cvxopt.glpk import ilp
import numpy as np
from cvxopt import matrix

big_cost=2500
DEBUG=0
n_stands=20 # number of stands
f = open("c:\\Users\\dell\\split.txt", "w")

# count the goal function of a solved model
# nn: number of customers=cabs (balanced)
# cost: cost matrix
# res: result received from solver
# show: debug
# sum: from which value to begin summing
# supply: current trips of taxis
# demand: new demand from customers
def count_sum(nn, cost, res, demand, supply, show, sum): ## nn is number of customers=cabs
    for taxi in range(0,nn): 
        for trip in range(0,nn): 
            if res[nn*taxi+trip]==1:
                if cost[taxi][trip] < big_cost:
                    if show==1:
                        f.write ("\ncab %d takes customer %d" % (supply[taxi][0], demand[trip][0])) # [0] is ID
                    sum = sum + dist[supply[taxi][2]][demand[trip][1]]
    return sum

# filters out elements in range - it maybe 'from', 'to' or 'id' which is checked
def filter (input, range, element, show, fil):
    count=0
    output=[]
    for row in input:
        if row[element] in range:
            output.append(row)
            if show==1:
                fil.write("(%d,%d,%d)" % (row[0], row[1], row[2]))
            count = count +1
    return count, output

# delivers random supply & demand
def rand_list(size, show):
    list = []
    count=0
    for i in range(0,size):
        frm = np.random.randint(0,size) # ID, from, to; from not used at that moment in cabs, 'to' not used in cust
        to = np.random.randint(0,size)
        if (frm != to): #  drop if equals
            list.append((count, frm, to))  # indices in Python start with 0
            if show==1:
                f.write("(%d,%d,%d)" % (count,frm,to))
            count = count +1 # TODO: list.size ?
    return count, list

def show_list(fil, label, list):
    fil.write ("\n%s:" % (label))
    for id, frm, to in list:
        fil.write("(%d,%d,%d)" % (id,frm,to))

# this rutine splits a model into four smaller models and finds their solutions + fifth solutions 
# for customers and cabs which have not been covered by these four previous ones
def solve_split (fil, size, distances, demand, cabs, show): # new demand, old demand
    if len(demand)==0 or len(cabs)==0:
        if show==1: fil.write("No demand or supply, quiting.")
        return

    rest_cust=[] # a list of IDs which have not been served
    rest_cabs=[] # which have not been used
    total=0
    start=0
    split_size = int(size/4)
    while start<size:
        r = range(start, start+ split_size)
        s=0
        nb_cust, split_demand = filter(demand, r, 1, 0, fil) # 1: frm; 0: NEVER DEBUG
        nb_cabs, split_cabs = filter(cabs, r, 2, 0, fil) # 2: to; 0: NEVER DEBUG
              
        if nb_cust>0 and nb_cabs>0:
            if show==1:
                fil.write ("\n\nRange %d to %d:" % (start, start+split_size))
                show_list (fil, "Customers", split_demand)
                show_list (fil, "Cabs", split_cabs)

            nb, x4, c = solve(distances, split_demand, split_cabs)
            
            # sum up the solution and pick up unsatisfied customers (and cabs)
            for taxi in range(0,nb): 
                for trip in range(0,nb): 
                    if x4[nb * taxi + trip]==1:
                        if c[taxi][trip] == big_cost: # big cost means customer not served
                            if nb_cabs > nb_cust: # there is always just one side dissatisfied
                                rest_cabs.append(split_cabs[taxi][0])
                            else: 
                                rest_cust.append(split_demand[trip][0])
                        else: # no, it is served
                            if show==1:
                                fil.write ("\ncab %d takes customer %d" % (split_cabs[taxi][0], split_demand[trip][0]))
                            s = s + dist[split_cabs[taxi][2]][split_demand[trip][1]]

            if show==1: fil.write ("\nSum: %d " % (s) )
        elif nb_cust>0: # this range has not been sendt to solver, but there might have beed demand or supply
            for d_id, d_frm, d_to in split_demand:
                rest_cust.append(d_id)
        else:
            for c_id, c_frm, c_to in split_cabs:
                rest_cabs.append(c_id)
        start = start + split_size
        total = total +s

    # now solve customers without a cab so far - the fifth run
    if show==1:
        fil.write ("\n\nUnserved customers and cabs without demand:")
        fil.write ("\nCustomers:")
    nb_cust, rest_demand = filter(demand, rest_cust, 0, DEBUG, fil)
    if show==1: fil.write ("\nCabs:")
    nb_cabs, rest_supply = filter(cabs, rest_cabs, 0, DEBUG, fil)
    
    nn, x5, c_table = solve(distances, rest_demand, rest_supply)
   
    return count_sum(nn, c_table, x5, rest_demand, rest_supply, DEBUG, total)
    
################################################

def calculate_cost (distances, demand, cabs):
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
   
    return n,cost
##################################################

def solve (distances, demand, cabs): # new demand, old demand
    n, cost = calculate_cost (distances, demand, cabs)
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

# heuristic - lowest cost method
# n: size
# c: cost
def LCM(n, c):
    temp=np.array(c) # copy the cost table
    d=temp.flatten()
    total_cost=0
    for i in range(0,n):  # so many iterations we have in LCM 
        elem = d.argmin(0) # 0 is first axis (there is just one)
        if d[elem]<big_cost: # do not sum a dummy cost
            total_cost += d[elem]
        
        row = int(elem/n)
        col = elem - row*n
        for j in range(0,n): 
            d[n*row+j]=big_cost; # marking row with a high cost
            d[j*n+col]=big_cost; # marking column 
    return total_cost
##############################

## MAIN ##
dist = np.zeros((n_stands,n_stands)) # distances
# calculate distances
for i in range(0,n_stands):
    for j in range(i,n_stands):
        dist[j][i]=j-i # simplification of distance - stop9 is closer to stop7 than to stop1
        dist[i][j]=dist[j][i] 

# ID, from, to; see the PDF file with explaination
if DEBUG==1: f.write ("The total demand:")
n_cust, new_demand = rand_list (n_stands, DEBUG)
if DEBUG==1: f.write ("\nThe total supply:")
n_cabs, current_trips = rand_list (n_stands, DEBUG)

# optimal solution, not split into four
nn, x, cost_table = solve(dist, new_demand, current_trips)

res = count_sum (nn, cost_table, x, new_demand, current_trips, DEBUG, 0)

f.write ("\n")
if DEBUG==1: f.write ("Sum: ")
f.write ("%d " % (res))

# now split the model into four

total = solve_split(f, n_stands, dist, new_demand, current_trips, DEBUG)
if DEBUG==1: f.write("\nSplit cost: ")
f.write("%d " % (total))

# just to be sure - count heuristic 
if DEBUG==1: f.write ("\nLCM sum: ")
lcm = LCM(nn, matrix(cost_table, tc='d'))
f.write ("%d " % (lcm))

f.close() 
