from cvxopt.glpk import ilp
import numpy as np
from cvxopt import matrix

n=100  
iter=1000
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
f= open("c:/Users/dell/Documents/TAXI-routing/Python/out.txt","w+")

for cycl in range(0,iter):
    c=matrix(np.random.randint(1,40,n*n), tc='d')
        
    # heuristic
    d=np.array(c)
    total_cost=0
    for i in range(0,n):  # so many iterations we have
        elem = d.argmin(0)
        total_cost += d[elem]
        row = int(elem/n)
        col = elem - row*n
        for j in range(0,n): 
            d[n*row+j]=100; # marking row with a high cost
            d[j*n+col]=100; # marking column 
    print(total_cost, end = '')
    print(' ', end = '')
    # optimum
    (status,x)=ilp(c,g.T,h,a,b,I,B)
    suma = sum(c.T*x)
    f.write("%5.1f; %5.1f; %5.1f\n" % (total_cost, suma, 100*
    (total_cost - suma)/suma) )
    if (suma > total_cost): f.write("!!!\n")
f.close() 