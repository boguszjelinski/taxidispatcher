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
#define MAX_ARR 100000

// four parameters that affect performance VERY much !!
#define n 1000  // number of customers/passengers

//int cost[n][n];
//int demand[n][5]; // id, from, to, maxWait, maxLoss
int ordersCount;
int poolSize;

//int pickup[MAX_THREAD][MAX_IN_POOL];
//int dropoff[MAX_THREAD][MAX_IN_POOL];
//int pool[MAX_THREAD][MAX_ARR][MAX_IN_POOL + MAX_IN_POOL + 1]; // pick-ups + drop-offs + cost
int poolAll[100000][MAX_IN_POOL + MAX_IN_POOL + 1];

int **poolPtr[MAX_THREAD];
int poolCount[MAX_THREAD];
int poolCountAll;
//int count_all_all = 0;
const char * fileName;
pthread_t th1[MAX_THREAD];
//struct ThreadData tData[MAX_THREAD];
int status[MAX_THREAD];
int step;

void setCosts(int ** cost) {
  for (int i=0; i<n; i++)
	for (int j=i; j<n; j++) {
		cost[j][i] = j-i; // simplification of distance - stop9 is closer to stop7 than to stop1
		cost[i][j] = cost[j][i] ;
	}
}

void readDemand(char * fileName, int linesNumb, int** demand)
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

int drop_customers(int t, int pool_count, int** cost, int** demand, int** pool, int* pickup, int* dropoff, int level, int custInPool) {
    if (level == custInPool) { // we now know how to pick up and drop-off customers
        //count_all_all++;
        int happy = 1; // true
        for (int d=0; d<custInPool; d++) { // checking if all 3(?) passengers get happy, starting from the one who gets dropped-off first
            int pool_cost=0;
            // cost of pick-up phase TODO: maybe pickup phase should not be counted to 'happiness' ?
            for (int ph=dropoff[d]; ph<custInPool-1; ph++)
                pool_cost += cost[demand[pickup[ph]][FROM]][demand[pickup[ph+1]][FROM]];
            // cost of first drop-off
            pool_cost += cost[demand[pickup[custInPool-1]][FROM]][demand[pickup[dropoff[0]]][TO]];
            // cost of drop-off
            for (int ph=0; ph<d; ph++)
                pool_cost += cost[demand[pickup[dropoff[ph]]][TO]][demand[pickup[dropoff[ph+1]]][TO]];
            if (pool_cost >  cost[demand[pickup[dropoff[d]]][FROM]][demand[pickup[dropoff[d]]][TO]]
																	* (1 + demand[pickup[dropoff[d]]][LOSS]/100.0)) {
                happy = 0; // not happy
                break;
            }
        }
        if (happy) {
            // add to pool
            for (int i=0; i<custInPool; i++) {
                pool[pool_count][i] = pickup[i];
                pool[pool_count][i+custInPool] = pickup[dropoff[i]];
            }
            int pool_cost = 0;
            for (int i=0; i<custInPool -1; i++) // cost of pick-up
                pool_cost += cost[demand[pickup[i]][FROM]][demand[pickup[i+1]][FROM]];
            pool_cost += cost[demand[pickup[custInPool-1]][FROM]][demand[pickup[dropoff[0]]][TO]]; // drop-off the first one
            for (int i=0; i<custInPool -1; i++) // cost of drop-off of the rest
                pool_cost += cost[demand[pickup[dropoff[i]]][TO]][demand[pickup[dropoff[i+1]]][TO]];
            // that is an imortant decision - is this the 'goal' function to be optimized ?
            pool[pool_count][MAX_IN_POOL + MAX_IN_POOL] = pool_cost;
            pool_count++;
        }
    } else for (int c=0; c<custInPool; c++) {
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
        pool_count = drop_customers(t, pool_count, cost, demand, pool, pickup, dropoff, level+1, custInPool);
    }
    return pool_count;
}

int findPool(int t, int pool_count, int** cost, int** demand, int** pool, int* pickup, int* dropoff, int level, int numbCust, int start, int stop, int custInPool) { // level of recursion = place in the pick-up queue
	if (level == 0)
		printf("START: t=%d start=%d stop=%d\n", t, start, stop);
    if (level == custInPool) { // now we have all customers for a pool (proposal) and their order of pick-up
        // next is to generate combinations for the "drop-off" phase
        pool_count = drop_customers(t, pool_count, cost, demand, pool, pickup, dropoff, 0, custInPool);
    } else for (int c = start; c < stop; c++) {
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
        if (p_cost > demand[pickup[level]][WAIT])
            continue;
        // find the next customer
        pool_count = findPool(t, pool_count, cost, demand, pool, pickup, dropoff, level+1, numbCust, 0, numbCust, custInPool);
    }
    return pool_count;
}

int** allocateArr(int rows, int cols) {
   int **pool_ptr, *ptr;
   int len = sizeof(int *) * rows + sizeof(int) * cols * rows;
   pool_ptr = (int **) malloc(len);
   // first element in of the array
   ptr = (int *)(pool_ptr + rows);

	//  to point rows pointer to appropriate location in the array
   for (int i = 0; i < rows; i++)
	  pool_ptr[i] = (ptr + cols * i);
   return pool_ptr;
}

// https://w3.cs.jmu.edu/kirkpams/OpenCSF/Books/csf/html/ThreadArgs.html
void *poolThread(void *args)
{  int len, col, *pickup_ptr, *dropoff_ptr, **pool_ptr, *ptr, **cost_ptr, **demand_ptr;
   int t = (int) args;

   cost_ptr = allocateArr(n,n);
   setCosts(cost_ptr);

   demand_ptr = allocateArr(n,5); // TODO: read from args, one read from disk for all threads
   readDemand(fileName, ordersCount, demand_ptr);

   pool_ptr = allocateArr(MAX_ARR, MAX_IN_POOL + MAX_IN_POOL + 1);

   len = sizeof(int) * MAX_IN_POOL ;
   pickup_ptr = (int *) malloc(len);
   dropoff_ptr = (int *) malloc(len);

   int start = t * step;
   int stop = start + step > ordersCount ? ordersCount : start + step;

   poolCount[t] = findPool(t, 0, cost_ptr, demand_ptr, pool_ptr, pickup_ptr, dropoff_ptr, 0, ordersCount, start, stop, poolSize);
   poolPtr[t] = pool_ptr;

   free(pickup_ptr);
   free(dropoff_ptr);
   free(cost_ptr);
   free(demand_ptr);
   pthread_exit(NULL);
}

void runThreads() {

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
  for (int t=0; t<MAX_THREAD; t++) {
	  for (int j=0; j<poolCount[t]; j++, poolCountAll++)
		  for (int i=0; i<MAX_IN_POOL + MAX_IN_POOL + 1; i++)
			  poolAll[poolCountAll][i] = poolPtr[t][j][i];
	  free(poolPtr[t]);
  }
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

    fileName = argv[3];
    ordersCount = atoi(argv[4]);
    poolSize = atoi(argv[1]);

    //readDemand(fileName, ordersCount); // filling 'demand' table

    //printf("ordersCount: %d\n", ordersCount);
    printf("Start: %s\n", now());

    step = (ordersCount / MAX_THREAD) + 1;
    runThreads();
    //findPool(0, 0, ordersCount, 0, 13, poolSize);
    //joinResults();
    //printf("Count ALL: %d\n", count_all_all);
    //printf("Count: %d\n", pool_count);
    //printf("Sorting ... %s\n", now());


    removeDuplicates();
    int good_count = 0;
    good_count = writeResult(argv[5], poolCountAll, poolSize);
    //printf("Not duplicated count: %d\n", good_count);
    printf("Stop: %s\n", now());
}

