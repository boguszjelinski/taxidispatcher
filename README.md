# taxi dispatcher
## How to optimally dispatch taxi cabs with linear programming

This repository contains code and one important PDF with theory which essentially show how to use a few techniques (a solver, heuristics, ...) for taxi dispatching - something I hope will help build wonderful, scalable self-driving taxi services. This repository was meant as a proof-of-concept and will find its continuation soon in form of a production-ready dispatcher/protocol, which could also be used to test different algorithms - see https://gitlab.com/kabina. This task extends beyond one-man capabilities - if you would like to join me or would like me to join a dedicated team - you will find my email in the [commit log](https://api.github.com/users/boguszjelinski/events/public). 

A few files with code are meant to explain or test different aspects of the problem:
File | Purpose |
----------|------------- |
python.py | shows how matrices are constructed, how they reflect the mathematical model |
julia.jl | Julia's version of python.py |
glkp.mod | glpk solver version of python.py |
procedure.py | an example of a procedure (solver) with human readable | input and output. But I don't recommend Python as a sheduler's core |
heuristic.py | it was used to compare optimal solutions to LCM |
pool_optimum.py | How 'pool' can be modeled. See the change in constraint matrices compared to python.py |
pool_opt_min.py | it was used to compare LCM to optimal solutions while looking for pools |
pool.c | C version of LCM for pools |
pool_n.c | a more advanced C version of LCM for pools. 4 passengers assigned within a few seconds. |
Pool.java | Java version of pool_n.c, astoundingly performant. |
perf.jl | it was used to check GLPK with one million variables |
split.py | to verify the degradation of results as a consequence of splitting a task into four smaller ones |
greedy_opt.py | mix of greedy and optimal solution in order to decrease size of model that is sent to solver. |
simulate.py | first try how it all could work |
gendemand.py | a generator of demand for the simulator in order to run simulations several times against the same input. |
Simulator.java | a real-world example how dispatching could work, a proof that handling 20k requests per hour is feasible. In fact, with 4 passengers in a pool much more is feasible. |

The Java Simulator produces a log so that you can verify the results, a few examples : 

    Time 0. Customer 116 assigned to and picked up by Cab 182 (POOL: the other Customer 204) (method LCM)
    Time 0. Customer 1 assigned by LCM to Cab 10
    Time 0. Customer 247 assigned in a pool as second passenger to Cab 10 (method LCM)
    Time 14. Cab 1205 is free at stand 7
    Time 18. Customer 7598 assigned to Cab 571, cab is heading to the customer (method LCM)
    Time 19. Customer 7263 picked up by Cab 357

There are also a few metrics that describe performance and other characteristics of the simulation: 

    Total customers: 42161
    Total abandoned customers: 2458
    Total picked up customers: 37337
    Total customers with assigned cabs: 37399
    Total simulation time [secs]: 2603
    Total pickup time: 5684
    Avg pickup time: 0
    Max model size: 1300
    Max solver size: 600
    Max solver time: 0
    Max LCM time: 0
    LCM use count: 120
    Max POOL time: 151
    Max POOL size: 722
    Total second customers in POOL: 18672