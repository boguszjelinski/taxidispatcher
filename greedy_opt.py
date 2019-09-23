from cvxopt.glpk import ilp
import numpy as np
from cvxopt import matrix

big_cost=250000
DEBUG=0
n_stands=4000 # number of stands
n_size = 400 # number of cabs and customers
THRESHOLD = 10 # stands - max distance for LCM
f = open("c:\\Users\\dell\\greedy_opt.txt", "a")

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

# filters out elements NOT in range - it maybe 'from', 'to' or 'id' which is checked
def filter_out (input, range, element):
    output=[]
    for row in input:
        if row[element] not in range:
            output.append(row)
    return len(output), output

# delivers random supply & demand
def rand_list(numb, size, show):
    list = []
    count=0
    for i in range(0,numb):
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

# heuristic - lowest cost method
# n: size
# c: cost
def LCM(n, c, show, fil):
    allocated_supply = []
    allocated_demand = []
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
        if d[elem]<big_cost: # do not sum a dummy cost
            total_cost += d[elem]
        if show==1: 
            fil.write ("(%d,%d), " % (row, col))
        for j in range(0,n): 
            d[n*row+j]=big_cost; # marking row with a high cost
            d[j*n+col]=big_cost; # marking column 
    return total_cost, allocated_supply, allocated_demand

##############################

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

## MAIN ##
dist = np.zeros((n_stands,n_stands)) # distances
# calculate distances
for i in range(0,n_stands):
    for j in range(i,n_stands):
        dist[j][i]=j-i # simplification of distance - stop9 is closer to stop7 than to stop1
        dist[i][j]=dist[j][i] 

# ID, from, to; see the PDF file with explaination
if DEBUG==1: f.write ("The total demand:")
n_cust, new_demand = rand_list (n_size, n_stands, DEBUG)
if DEBUG==1: f.write ("\nThe total supply:")
n_cabs, current_trips = rand_list (n_size, n_stands, DEBUG)

# optimal solution, not split into four
nn, x, cost_table = solve(dist, new_demand, current_trips)

res = count_sum (nn, cost_table, x, new_demand, current_trips, DEBUG, 0)

f.write ("\n")
if DEBUG==1: f.write ("Sum: ")
f.write ("%d %d " % (nn, res))

# just to be sure - count heuristic 
if DEBUG==1: f.write ("\nAllocated pairs (cab,customer): ")
lcm, allocated_cabs, allocated_cust = LCM(nn, matrix(cost_table, tc='d').T, DEBUG, f)
if DEBUG==1: 
    f.write ("\nLCM sum: ")
    f.write ("%d \n" % (lcm))

nb_cust, rest_demand = filter_out(new_demand, allocated_cust, 0) 
if DEBUG==1: show_list(f, "Rest demand: ", rest_demand)
nb_cabs, rest_cabs = filter_out(current_trips, allocated_cabs, 0) 
if DEBUG==1: show_list(f, "Rest cabs: ", rest_cabs)

n2, x2, cost_table2 = solve(dist, rest_demand, rest_cabs)
res2 = count_sum (n2, cost_table2, x2, rest_demand, rest_cabs, DEBUG, 0)

if DEBUG==1: f.write ("\nSum2: ")
f.write ("%d %d " % (n2, res2+lcm))


f.close() 
