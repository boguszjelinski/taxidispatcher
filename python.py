# Author: Bogusz Jelinski
# A.D. 2020
from cvxopt.glpk import ilp
import numpy as np
from cvxopt import matrix
n=4
c=matrix(   [5,5,0,5, 1,1,3,8, 9,9,5,0, 100,100,100,100], tc='d')
a=matrix([  [1,1,1,1, 0,0,0,0, 0,0,0,0, 0,0,0,0],  # sum for cab0
            [0,0,0,0, 1,1,1,1, 0,0,0,0, 0,0,0,0],
            [0,0,0,0, 0,0,0,0, 1,1,1,1, 0,0,0,0],
            [0,0,0,0, 0,0,0,0, 0,0,0,0, 1,1,1,1],
            [1,0,0,0, 1,0,0,0, 1,0,0,0, 1,0,0,0], # sum for customer0
            [0,1,0,0, 0,1,0,0, 0,1,0,0, 0,1,0,0],
            [0,0,1,0, 0,0,1,0, 0,0,1,0, 0,0,1,0],
            [0,0,0,1, 0,0,0,1, 0,0,0,1, 0,0,0,1]
           ],tc='d')
g=matrix([  [0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0] ], tc='d')
b=matrix(1*np.ones(n*2)) # all constraints equals '1'
h=matrix(0*np.ones(1))
I=set(range(n*n))
B=set(range(n*n))
(status,x)=ilp(c,g.T,h,a.T,b,I,B)
status
print(x)
print('objective',sum(c.T*x))