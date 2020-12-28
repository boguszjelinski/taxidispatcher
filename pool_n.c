#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <pthread.h>

#define ID 0
#define FROM 1
#define TO 2
#define WAIT 3
#define LOSS 4

#define MAX_IN_POOL 4
#define MAX_THREAD 8

// four parameters that affect performance VERY much !!
#define n 1000  // number of customers/passengers

int cost[n][n];
int demand[n][5]; // id, from, to, maxWait, maxLoss
int ordersCount;
int poolSize;

int pickup[MAX_THREAD][MAX_IN_POOL];
int dropoff[MAX_THREAD][MAX_IN_POOL];
int pool[MAX_THREAD][100000][MAX_IN_POOL + MAX_IN_POOL + 1]; // pick-ups + dropp-offs + cost
int poolAll[100000][MAX_IN_POOL + MAX_IN_POOL + 1];
int pool_count[MAX_THREAD];
int poolCountAll;
int count_all = 0;

pthread_t th1[MAX_THREAD];
int status[MAX_THREAD];
int start[MAX_THREAD];
int stop[MAX_THREAD];

void readDemand(char * fileName, int linesNumb)
{
    FILE * fp;
    char line[40];
    size_t len = 0;
    ssize_t read;

    fp = fopen(fileName, "r");
    if (fp == NULL) {
        printf("Opening file %s failed\n", fileName);
        exit(EXIT_FAILURE);
    }
    char* tok;
    int rec = 0;
    while (fgets(line, sizeof(line), fp)) {
        // five fields expected
        tok = strtok(line, ",");
        for (int i=0; i<5; i++) {
            demand[rec][i] = atoi(tok);
            tok = strtok(NULL, ",");
            if (tok == NULL) break;
        }
        rec++;
        if (rec == linesNumb) break;
    }
    fclose(fp);
}

int writeResult(char * fileName, int linesNumb, int poolSize)
{	int count = 0;
    FILE * fp;
    fp = fopen(fileName, "w");
    if (fp == NULL) {
        printf("Opening file %s failed\n", fileName);
        exit(EXIT_FAILURE);
    }
    for (int i =0; i<linesNumb; i++)
     if (poolAll[i][0] != -1) {
    	for (int j =0; j < poolSize; j++)
    		fprintf(fp, "%d,", poolAll[i][j]);
    	for (int j =0; j < poolSize; j++)
    	    fprintf(fp, "%d,", poolAll[i][poolSize + j]);
    	fprintf(fp, "\n");
    	count++;
    }
    fclose(fp);
    return count;
}


// compare func for Qsort
int cmp ( const void *pa, const void *pb ) {
    const int (*a)[MAX_IN_POOL+MAX_IN_POOL+1] = pa;
    const int (*b)[MAX_IN_POOL+MAX_IN_POOL+1] = pb;
    // the last cell is the value to be sorted by (cost of a trip)
    if ( (*a)[MAX_IN_POOL+MAX_IN_POOL] < (*b)[MAX_IN_POOL+MAX_IN_POOL] ) return -1;
    if ( (*a)[MAX_IN_POOL+MAX_IN_POOL] > (*b)[MAX_IN_POOL+MAX_IN_POOL] ) return +1;
    return 0;
}

char *now(){
    char *stamp = (char *)malloc(sizeof(char) * 16);
    time_t lt = time(NULL);
    struct tm *tm = localtime(&lt);
    sprintf(stamp,"%04d-%02d-%02d %02d:%02d:%02d", tm->tm_year+1900, tm->tm_mon+1, tm->tm_mday, tm->tm_hour, tm->tm_min, tm->tm_sec);
    return stamp;
}

void drop_customers(int t, int level, int custInPool) {
    if (level == custInPool) { // we now know how to pick up and drop-off customers
        count_all++;
        int happy = 1; // true
        for (int d=0; d<custInPool; d++) { // checking if all 3(?) passengers get happy, starting from the one who gets dropped-off first
            int pool_cost=0;
            // cost of pick-up phase TODO: maybe pickup[t] phase should not be counted to 'happiness' ?
            for (int ph=dropoff[t][d]; ph<custInPool-1; ph++)
                pool_cost += cost[demand[pickup[t][ph]][FROM]][demand[pickup[t][ph+1]][FROM]];
            // cost of first drop-off
            pool_cost += cost[demand[pickup[t][custInPool-1]][FROM]][demand[pickup[t][dropoff[t][0]]][TO]];
            // cost of drop-off
            for (int ph=0; ph<d; ph++)
                pool_cost += cost[demand[pickup[t][dropoff[t][ph]]][TO]][demand[pickup[t][dropoff[t][ph+1]]][TO]];
            if (pool_cost >  cost[demand[pickup[t][dropoff[t][d]]][FROM]][demand[pickup[t][dropoff[t][d]]][TO]]
																	* (1 + demand[pickup[t][dropoff[t][d]]][LOSS]/100.0)) {
                happy = 0; // not happy
                break;
            }
        }
        if (happy) {
            // add to pool
            for (int i=0; i<custInPool; i++) {
                pool[t][pool_count[t]][i] = pickup[t][i];
                pool[t][pool_count[t]][i+custInPool] = pickup[t][dropoff[t][i]];
            }
            int pool_cost = 0;
            for (int i=0; i<custInPool -1; i++) // cost of pick-up
                pool_cost += cost[demand[pickup[t][i]][FROM]][demand[pickup[t][i+1]][FROM]];
            pool_cost += cost[demand[pickup[t][custInPool-1]][FROM]][demand[pickup[t][dropoff[t][0]]][TO]]; // drop-off the first one
            for (int i=0; i<custInPool -1; i++) // cost of drop-off of the rest
                pool_cost += cost[demand[pickup[t][dropoff[t][i]]][TO]][demand[pickup[t][dropoff[t][i+1]]][TO]];
            // that is an imortant decision - is this the 'goal' function to be optimized ?
            pool[t][pool_count[t]][MAX_IN_POOL + MAX_IN_POOL] = pool_cost;
            pool_count[t]++;
        }
    } else for (int c=0; c<custInPool; c++) {
        // check if 'c' not in use in previous levels - "variation without repetition"
        int found=0;
        for (int l=0; l<level; l++) {
           if (dropoff[t][l] == c) {
               found = 1;
               break;
           };
        }
        if (found) continue; // next proposal please
        dropoff[t][level] = c;
        // a drop-off more
        drop_customers(t, level+1, custInPool);
    }
}

void findPool(int t, int level, int numbCust, int start, int stop, int custInPool) { // level of recursion = place in the pick-up queue
	if (level == 0)
		printf("t=%d start=%d stop=%d\n", t, start, stop);
    if (level == custInPool) { // now we have all customers for a pool (proposal) and their order of pick-up
        // next is to generate combinations for the "drop-off" phase
        drop_customers(t, 0, custInPool);
    } else for (int c = start; c < stop; c++) {
        // check if 'c' not in use in previous levels - "variation without repetition"
        int found=0;
        for (int l=0; l<level; l++) {
           if (pickup[t][l] == c) {
               found = 1;
               break;
           };
        }
        if (found) continue; // next proposal please
        pickup[t][level] = c;
        // check if the customer is happy, that he doesn't have to wait for too long
        int p_cost = 0;
        for (int l = 0; l < level; l++)
            p_cost += cost[demand[pickup[t][l]][FROM]][demand[pickup[t][l+1]][FROM]];
        if (p_cost > demand[pickup[t][level]][WAIT])
            continue;
        // find the next customer
        findPool(t, level+1, numbCust, 0, numbCust, custInPool);
    }
}

void setCosts() {
  for (int i=0; i<n; i++)
	for (int j=i; j<n; j++) {
		cost[j][i] = j-i; // simplification of distance - stop9 is closer to stop7 than to stop1
		cost[i][j] = cost[j][i] ;
	}
}

// https://w3.cs.jmu.edu/kirkpams/OpenCSF/Books/csf/html/ThreadArgs.html
void *poolThread(void *args)
{
   int t = (int) args;
   findPool(t, 0, ordersCount, start[t], stop[t], poolSize);
   pthread_exit(NULL);
}

void runThreads() {
  int step = (ordersCount / MAX_THREAD) + 1;

  for (int i=0, s=0; i<MAX_THREAD; i++, s+=step) {
	start[i] = s;
	stop[i] = s + step > ordersCount ? ordersCount : s + step; // last thread may get a bit fewer orders
  }
  for (int i=0; i<MAX_THREAD; i++)
	pthread_create(&th1[i], NULL, poolThread, (void*)i);

  // wait for all threads to complete
  for (int i=0; i<MAX_THREAD; i++)
	pthread_join(th1[i], &status[i]);

  //copying results from all threads to one table - to be sorted
  joinResults();
}

void joinResults() {
  poolCountAll=0;
  for (int t=0; t<MAX_THREAD; t++)
	  for (int j=0; j<pool_count[t]; j++, poolCountAll++)
		  for (int i=0; i<MAX_IN_POOL + MAX_IN_POOL + 1; i++)
			  poolAll[poolCountAll][i] = pool[t][j][i];
}

void removeDuplicates() {

    qsort(poolAll, poolCountAll, sizeof poolAll[0], cmp);
    //printf("Removing duplicates ... %s\n", now());
    for (int i=0; i < poolCountAll; i++) {
      if (poolAll[i][0] == -1) continue;
      for (int j=i+1; j<poolCountAll; j++)
        if (poolAll[j][0] != -1) { // not invalidated; for performance reasons
            int found = 0; // false
            for (int x=0; x<MAX_IN_POOL; x++) {
                for (int y=0; y<MAX_IN_POOL; y++)
                    if (poolAll[j][x] == poolAll[i][y]) {
                        found = 1;
                        break;
                    }
                if (found) break;
            }
            if (found) poolAll[j][0] = -1; // duplicated
        }
    }
}

int main(int argc, char *argv[])
{
    if (argc != 6) {
        printf("Usage: cmd pool-size threads-number demand-file-name rec-number output-file");
        exit(EXIT_FAILURE);
    }

    const char * fileName = argv[3];
    ordersCount = atoi(argv[4]);
    poolSize = atoi(argv[1]);

    readDemand(fileName, ordersCount); // filling 'demand' table

    //printf("ordersCount: %d\n", ordersCount);
    //printf("Start: %s\n", now());

    setCosts();
    //runThreads();
    findPool(0, 0, ordersCount, 0, 13, poolSize);
    joinResults();
    //printf("Count ALL: %d\n", count_all);
    //printf("Count: %d\n", pool_count);
    //printf("Sorting ... %s\n", now());


    removeDuplicates();
    int good_count = 0;
    good_count = writeResult(argv[5], poolCountAll, poolSize);
    //printf("Not duplicated count: %d\n", good_count);
    //printf("Stop: %s\n", now());
}
