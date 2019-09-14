# taxidispatcher
## How to optimally dispatch taxi cabs with linear programming

This repository contains code (and one important PDF) which mainly shows how to use the GLPK solver for taxi dispatching - something I hope will help some companies build wonderful self-driving taxi services. 

Files with code are meant to explain different aspects of the problem:

python.py: shows how matrices are constructed, how they reflect the mathematical model

heuristic.py: it was used to compare optimal solutions to LCM

procedure.py: an example of a procedure with human readable input and output

pool_optimum.py: see the change in constraint matrices

pool_opt_min.py: it was used to compare LCM to optimal solutions while looking for pools

pool.c: C version of LCM for pools

julia.jl: Julia's version of python.py

perf.jl: it was used to check GLPK with one million variables

split.py: to verify the degradation of results as a consequence of splitting a task into four smaller