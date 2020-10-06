/* Author: Bogusz Jelinski
   A.D.: 2020
   Title: taxi pool simulator
   Description: just to test Java performance (well, runs like a rocket)
*/

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class Pool {

    final static int FROM = 0;
    final static int TO = 1;
    // four most important parameters 
    final static int n = 800;  // number of customers/passengers
    final static int MAX_IN_POOL = 4;  // how many passengers can a cab take
    final static int MAX_WAIT_TIME = 10; // how many 'minutes' (time entities) want a passenger to wait for a cab
    final static double max_loss = 1.01; // 1.3 would mean that one can accept a trip to take 30% time more than being transported alone
    
    static int [][]cost  = new int[n][n];
    static int [][]demand= new int[n][2]; // from, to
    static int []pickup  = new int[MAX_IN_POOL]; // will have numbers of customers
    static int []dropoff = new int[MAX_IN_POOL]; // will have indexes to 'pickup' table
    static int numb_cust = n; // maybe *2? twice as many customers than there are stops;
    static int count = 0;
    static int count_all = 0;
    static List<PoolEl> poolList = new ArrayList<PoolEl>();

    static void drop_customers(int level) {
        if (level == MAX_IN_POOL) { // we now know how to pick up and drop-off customers
            count_all++;
            boolean happy = true;
            for (int d=0; d<MAX_IN_POOL; d++) { // checking if all 3(?) passengers get happy, starting from the one who gets dropped-off first
                int pool_cost=0;
                // cost of pick-up phase
                for (int ph=dropoff[d]; ph<MAX_IN_POOL-1; ph++)
                    pool_cost += cost[demand[pickup[ph]][FROM]][demand[pickup[ph+1]][FROM]];
                // cost of first drop-off
                pool_cost += cost[demand[pickup[MAX_IN_POOL-1]][FROM]][demand[pickup[dropoff[0]]][TO]];
                // cost of drop-off
                for (int ph=0; ph<d; ph++)
                    pool_cost += cost[demand[pickup[dropoff[ph]]][TO]][demand[pickup[dropoff[ph+1]]][TO]];
                if (pool_cost >  cost[demand[pickup[dropoff[d]]][FROM]][demand[pickup[dropoff[d]]][TO]]*max_loss) {
                    happy = false; // not happy
                    break;
                }
            }
            if (happy) {
                // add to pool
                PoolEl pool = new PoolEl();
                pool.cust = new int[MAX_IN_POOL + MAX_IN_POOL];

                for (int i=0; i<MAX_IN_POOL; i++) {
                    pool.cust[i] = pickup[i];
                    pool.cust[i+MAX_IN_POOL] = pickup[dropoff[i]];
                }
                int pool_cost = 0;
                for (int i=0; i<MAX_IN_POOL -1; i++) // cost of pick-up
                    pool_cost += cost[demand[pickup[i]][FROM]][demand[pickup[i+1]][FROM]];
                pool_cost += cost[demand[pickup[MAX_IN_POOL-1]][FROM]][demand[pickup[dropoff[0]]][TO]]; // drop-off the first one
                for (int i=0; i<MAX_IN_POOL -1; i++) // cost of drop-off of the rest
                    pool_cost += cost[demand[pickup[dropoff[i]]][TO]][demand[pickup[dropoff[i+1]]][TO]];
                // that is an imortant decision - is this the 'goal' function to be optimized ?
                pool.cost = pool_cost;
                poolList.add(pool);
                count++;
            }
        } else for (int c=0; c<MAX_IN_POOL; c++) { 
            // check if 'c' not in use in previous levels - "variation without repetition"
            boolean found = false;
            for (int l=0; l<level; l++) {
               if (dropoff[l] == c) {
                   found = true;
                   break;
               }; 
            }
            if (found) continue; // next proposal please
            dropoff[level] = c;
            // a drop-off more
            drop_customers(level+1);
        }
    }
    
    static void poolv2(int level) { // level of recursion = place in the pick-up queue
        if (level == MAX_IN_POOL) { // now we have all customers for a pool (proposal) and their order of pick-up
            // next is to generate combinations for the "drop-off" phase
            drop_customers(0);
        } else for (int c=0; c < numb_cust; c++) {
            // check if 'c' not in use in previous levels - "variation without repetition"
            boolean found=false;
            for (int l=0; l<level; l++) {
               if (pickup[l] == c) {
                   found = true;
                   break;
               }; 
            }
            if (found) continue; // next proposal please
            pickup[level] = c;
            // check if the customer is happy, that he doesn't have to wait for too long
            int p_cost = 0;
            for (int l = 0; l < level; l++) 
                p_cost += cost[demand[pickup[l]][FROM]][demand[pickup[l+1]][FROM]];
            if (p_cost > MAX_WAIT_TIME)
                continue;
            // find the next customer
            poolv2(level+1);
        }
    }
 
    public static void main(String[] args) {
        for (int i=0; i<n; i++)
            for (int j=i; j<n; j++) {
                cost[j][i] = j-i; // simplification of distance - stop9 is closer to stop7 than to stop1
                cost[i][j]=cost[j][i] ;
            }

        for (int i=0; i<numb_cust; i++) {
            demand[i][FROM] = ThreadLocalRandom.current().nextInt(0, n); 
            demand[i][TO] = ThreadLocalRandom.current().nextInt(0, n);
        }
        // now we have to remove incidental from=to generated by 'random'
        int length = numb_cust; // *2: let's have twice as much customers than there are stops;
        int i=0;
        while (i<length)
            if (demand[i][0]==demand[i][1]) {
                if (demand[i][0] == 0) {
                    demand[i][0] = 1;
                }
                else {
                    demand[i][0]--;
                }
            }
            else i++;
    
   
        // for (int i=0; i<numb_cust; i++)
        //     printf ("customer %d: from: %d to: %d\n", i, demand[i][0], demand[i][1]);
        System.out.println("numb_cust: " +  numb_cust);
        //show_cost();
        poolv2(0);
        // sorting
        PoolEl[] arr = poolList.toArray(new PoolEl[poolList.size()]);
        Arrays.sort(arr);
        // removin duplicates
        for (i = 0; i<arr.length; i++) {
            if (arr[i].cost == -1) continue;
            for (int j = i+1; j < arr.length; j++)
                if (arr[j].cost != -1) { // not invalidated; for performance reasons
                    boolean found = false;
                    for (int x=0; x<MAX_IN_POOL; x++) {
                        for (int y=0; y<MAX_IN_POOL; y++)
                            if (arr[j].cust[x] == arr[i].cust[y]) {   
                                found = true; 
                                break;
                            } 
                        if (found) break;
                    }
                    if (found) arr[j].cost = -1; // duplicated
                }
        }
        int good_count=0;
        for (i=0; i<arr.length; i++) 
            if (arr[i].cost != -1) { 
                good_count++;
            }
        System.out.println("Not duplicated count2: " + good_count);
    }

    private static class PoolEl implements Comparable<PoolEl> {
	    public int [] cust;
	    public int cost;
	 
	    PoolEl () {}
	    
	    @Override
	    public int compareTo(PoolEl pool) {
	        return this.cost - pool.cost;
	    }
	}
}
