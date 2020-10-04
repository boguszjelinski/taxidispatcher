#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define FROM 0
#define TO 1

// four parameters that affect performance VERY much !!
#define n 1000  // number of customers/passengers
#define MAX_IN_POOL  4  // how many passengers can a cab take
#define MAX_WAIT_TIME 10 // how many 'minutes' (time entities) want a passenger to wait for a cab
const float max_loss=1.01; // with value of 1.3 - I don't care if it takes 30% longer with pool than without

int cost[n][n];
int demand[n][2]; // from, to
int numb_cust = n; // maybe *2? twice as many customers than there are stops;
int pickup[MAX_IN_POOL];
int dropoff[MAX_IN_POOL];
int pool_v2[2000000][MAX_IN_POOL + MAX_IN_POOL + 1]; // pick-ups + dropp-offs + cost
int count_v2 = 0;
int count_v2_ALL = 0;

// compare func for Qsort
int cmp ( const void *pa, const void *pb ) {
    const int (*a)[MAX_IN_POOL+MAX_IN_POOL+1] = pa;
    const int (*b)[MAX_IN_POOL+MAX_IN_POOL+1] = pb;
    // the last cell is the value to be sorted by (cost of a trip)
    
    if ( (*a)[MAX_IN_POOL+MAX_IN_POOL] < (*b)[MAX_IN_POOL+MAX_IN_POOL] ) return -1; 
    if ( (*a)[MAX_IN_POOL+MAX_IN_POOL] > (*b)[MAX_IN_POOL+MAX_IN_POOL] ) return +1;
    return 0;
}

void show_cost () {
    for (int i=0; i<n; i++) {
            printf ("%d: ", i);
            for (int j=0; j<n; j++) printf ("%3d ", cost[i][j]);
            printf ("\n");
        }
}

char *now(){
    char *stamp = (char *)malloc(sizeof(char) * 16);
    time_t lt = time(NULL);
    struct tm *tm = localtime(&lt);
    sprintf(stamp,"%04d-%02d-%02d %02d:%02d:%02d", tm->tm_year+1900, tm->tm_mon+1, tm->tm_mday, tm->tm_hour, tm->tm_min, tm->tm_sec);
    return stamp;
}

void drop_customers(int level) {
    if (level == MAX_IN_POOL) { // we now know how to pick up and drop-off customers
        count_v2_ALL++;
        int happy = 1; // true
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
                happy = 0; // not happy
                break;
            }
        }
        if (happy) {
            // add to pool
            for (int i=0; i<MAX_IN_POOL; i++) {
                pool_v2[count_v2][i]=pickup[i];
                pool_v2[count_v2][i+MAX_IN_POOL]=pickup[dropoff[i]];
            }
            int pool_cost = 0;
            for (int i=0; i<MAX_IN_POOL -1; i++) // cost of pick-up
                pool_cost += cost[demand[pickup[i]][FROM]][demand[pickup[i+1]][FROM]];
            pool_cost += cost[demand[pickup[MAX_IN_POOL-1]][FROM]][demand[pickup[dropoff[0]]][TO]]; // drop-off the first one
            for (int i=0; i<MAX_IN_POOL -1; i++) // cost of drop-off of the rest
                pool_cost += cost[demand[pickup[dropoff[i]]][TO]][demand[pickup[dropoff[i+1]]][TO]];
            // that is an imortant decision - is this the 'goal' function to be optimized ?
            pool_v2[count_v2][6] = pool_cost;
            count_v2++;
        }
    } else for (int c=0; c<MAX_IN_POOL; c++) { 
        // check if 'c' not in use in previous levels - "variation without repetition"
        int found=0;
        for (int l=0; l<level; l++) {
           if (dropoff[l] == c) {
               found = 1;
               break;
           }; 
        }
        if (found) continue; // next proposal please
        dropoff[level] = c;
        // a drop-off more
        drop_customers(level+1);
    }
}

void poolv2(int level) { // level of recursion = place in the pick-up queue
    if (level == MAX_IN_POOL) { // now we have all customers for a pool (proposal) and their order of pick-up
        // next is to generate combinations for the "drop-off" phase
        drop_customers(0);
    } else for (int c=0; c < numb_cust; c++) {
        // check if 'c' not in use in previous levels - "variation without repetition"
        int found=0;
        for (int l=0; l<level; l++) {
           if (pickup[l] == c) {
               found = 1;
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

int main(void)
{
    int pool_numb = 0;
    // time_t t;
    // srand((unsigned) time(&t));

    for (int i=0; i<n; i++)
        for (int j=i; j<n; j++) {
            cost[j][i] = j-i; // simplification of distance - stop9 is closer to stop7 than to stop1
            cost[i][j]=cost[j][i] ;
        }
    for (int i=0; i<numb_cust; i++) {
        demand[i][0] = rand() % n; // 0: from 
        demand[i][1] = rand() % n; // 1: to
    }
    // now we have to remove incidental from=to generated by 'random'
    int length = numb_cust; // *2: let's have twice as much customers than there are stops;
    int i=0;
    while (i<length)
        if (demand[i][0]==demand[i][1]) {
            memmove(&demand[i], &demand[i+1], (length-i-1) * sizeof(demand[0]));
            length--;
        }
        else i++;

    numb_cust=length;

    // for (int i=0; i<numb_cust; i++)
    //     printf ("customer %d: from: %d to: %d\n", i, demand[i][0], demand[i][1]);
    printf("numb_cust: %d\n", numb_cust);
    //show_cost();

    printf("Start: %s\n", now());
    poolv2(0);
    printf("Sorting ... %s\n", now());
    qsort(pool_v2, count_v2, sizeof pool_v2[0], cmp);
    printf("Count2 ALL: %d\n", count_v2_ALL);
    printf("Count2: %d\n", count_v2);

    printf("Removing duplicates ... %s\n", now());
    for (int i=0; i<count_v2; i++) {
      if (pool_v2[i][0] == -1) continue;
      for (int j=i+1; j<count_v2; j++)
        if (pool_v2[j][0] != -1) { // not invalidated; for performance reasons
            int found = 0; // false
            for (int x=0; x<MAX_IN_POOL; x++) {
                for (int y=0; y<MAX_IN_POOL; y++)
                    if (pool_v2[j][x] == pool_v2[i][y]) {   
                        found = 1; 
                        break;
                    } 
                if (found) break;
            }
            if (found) pool_v2[j][0] = -1; // duplicated
        }
    }
    int good_count=0;
    for (int i=0; i<count_v2; i++) 
        if (pool_v2[i][0]!= -1) { 
            good_count++;
            // printf("%d: Pick up: %d %d %d Drop off: %d %d %d\n", 
            //             i, pool_v2[i][0], pool_v2[i][1], pool_v2[i][2], pool_v2[i][3], pool_v2[i][4], pool_v2[i][5]);
        }
    printf("Not duplicated count2: %d\n", good_count);
    printf("Stop: %s\n", now());
}
