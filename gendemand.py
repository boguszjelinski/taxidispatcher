import numpy as np
hours = 2
reqs_per_minute=400 
n_stands = 50
f = open("c:\\home\\dell\\taxi_demand.txt", "w")
idx=0
for time in range(0,hours*60):
    for k in range (0, np.random.randint(0,reqs_per_minute*2)): # average number per minute should be reqs_per_minute, right?
        frm = np.random.randint(0,n_stands) # ID, from, to; from not used at that moment in cabs, 'to' not used in cust
        todiff = np.random.randint(-4,4) # limit the trip to a short range, like NYC
        to = 0
        if todiff == 0: continue
        elif todiff > 0 and frm + todiff >= n_stands: frm = n_stands-1
        elif todiff < 0 and frm + todiff < 0: frm = 0
        else: to = frm + todiff
        if frm == to: continue
        wait = np.random.randint(0,10) # from 0 to ten minutes
        if wait<5: wait=0 # half of customers witl have "now"
        f.write ("(%d,%d,%d,%d,%d)\n" % (idx, frm, to, time, time+wait))
        idx = idx+1
f.close()