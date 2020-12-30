// ConsoleApplication1.cpp : This file contains the 'main' function. Program execution begins and ends there.
//

#include <iostream>
#include <thread>
#include <string.h>

#define ID 0
#define FROM 1
#define TO 2
#define WAIT 3
#define LOSS 4

#define MAX_IN_POOL 4
#define MAX_THREAD 8
#define MAX_ARR 10000

// four parameters that affect performance VERY much !!
int n = 100;  // number of customers/passengers

//int cost[n][n];
//int demand[n][5]; // id, from, to, maxWait, maxLoss
int ordersCount;
int poolSize;

//int pickup[MAX_THREAD][MAX_IN_POOL];
//int dropoff[MAX_THREAD][MAX_IN_POOL];
//int pool[MAX_THREAD][MAX_ARR][MAX_IN_POOL + MAX_IN_POOL + 1]; // pick-ups + drop-offs + cost
int poolAll[10000][MAX_IN_POOL + MAX_IN_POOL + 1];

int** poolPtr[MAX_THREAD];
int poolCount[MAX_THREAD];
int poolCountAll;
//int count_all_all = 0;
char fileName[] = "pool-in.csv";
//pthread_t th1[MAX_THREAD];
//struct ThreadData tData[MAX_THREAD];
int status[MAX_THREAD];
int step;

void setCosts(int** cost) {
    for (int i = 0; i < n; i++)
        for (int j = i; j < n; j++) {
            cost[j][i] = j - i; // simplification of distance - stop9 is closer to stop7 than to stop1
            cost[i][j] = cost[j][i];
        }
}

void readDemand(char* fileName, int linesNumb, int** demand)
{
    int dem[100][5] = { {0, 0, 1, 20, 20},
        {1, 1, 2, 20, 20},
        {2, 2, 3, 20, 20},
        {3, 3, 4, 20, 20},
        {4, 4, 5, 20, 20},
        {5, 5, 6, 20, 20},
        {6, 6, 7, 20, 20},
        {7, 7, 8, 20, 20},
        {8, 8, 9, 20, 20},
        {9, 9, 10, 20, 20},
        {10, 10, 11, 20, 20},
        {11, 11, 12, 20, 20},
        {12, 12, 13, 20, 20},
        {13, 13, 14, 20, 20},
        {14, 14, 15, 20, 20},
        {15, 15, 16, 20, 20},
        {16, 16, 17, 20, 20},
        {17, 17, 18, 20, 20},
        {18, 18, 19, 20, 20},
        {19, 19, 20, 20, 20},
        {20, 20, 21, 20, 20 },
    { 21, 21, 22, 20, 20 },
    { 22, 22, 23, 20, 20 },
    { 23, 23, 24, 20, 20 },
    { 24, 24, 25, 20, 20 },
    { 25, 25, 26, 20, 20 },
    { 26, 26, 27, 20, 20 },
    { 27, 27, 28, 20, 20 },
    { 28, 28, 29, 20, 20 },
    { 29, 29, 30, 20, 20 },
    { 30, 30, 31, 20, 20 },
    { 31, 31, 32, 20, 20 },
    { 32, 32, 33, 20, 20 },
    { 33, 33, 34, 20, 20 },
    { 34, 34, 35, 20, 20 },
    { 35, 35, 36, 20, 20 },
    { 36, 36, 37, 20, 20 },
    { 37, 37, 38, 20, 20 },
    { 38, 38, 39, 20, 20 },
    { 39, 39, 40, 20, 20 },
    { 40, 40, 41, 20, 20 },
    { 41, 41, 42, 20, 20 },
    { 42, 42, 43, 20, 20 },
    { 43, 43, 44, 20, 20 },
    { 44, 44, 0, 20, 20 },
    { 45, 0, 1, 20, 20 },
    { 46, 1, 2, 20, 20 },
    { 47, 2, 3, 20, 20 },
    { 48, 3, 4, 20, 20 },
    { 49, 4, 5, 20, 20 },
    { 50, 5, 6, 20, 20 },
    { 51, 6, 7, 20, 20 },
    { 52, 7, 8, 20, 20 },
    { 53, 8, 9, 20, 20 },
    { 54, 9, 10, 20, 20 },
    { 55, 10, 11, 20, 20 },
    { 56, 11, 12, 20, 20 },
    { 57, 12, 13, 20, 20 },
    { 58, 13, 14, 20, 20 },
    { 59, 14, 15, 20, 20 },
    { 60, 15, 16, 20, 20 },
    { 61, 16, 17, 20, 20 },
    { 62, 17, 18, 20, 20 },
    { 63, 18, 19, 20, 20 },
    { 64, 19, 20, 20 , 20},
    { 65, 20, 21, 20, 20 },
    { 66, 21, 22, 20, 20 },
    { 67, 22, 23, 20, 20 },
    { 68, 23, 24, 20, 20 },
    { 69, 24, 25, 20, 20 },
    { 70, 25, 26, 20, 20 },
    { 71, 26, 27, 20, 20 },
    { 72, 27, 28, 20, 20 },
    { 73, 28, 29, 20, 20 },
    { 74, 29, 30, 20, 20 },
    { 75, 30, 31, 20, 20 },
    { 76, 31, 32, 20, 20 },
    { 77, 32, 33, 20, 20 },
    { 78, 33, 34, 20, 20 },
    { 79, 34, 35, 20, 20 },
    { 80, 35, 36, 20, 20 },
    { 81, 36, 37, 20, 20 },
    { 82, 37, 38, 20, 20 },
    { 83, 38, 39, 20, 20 },
    { 84, 39, 40, 20, 20 },
    { 85, 40, 41, 20, 20 },
    { 86, 41, 42, 20, 20 },
    { 87, 42, 43, 20, 20 },
    { 88, 43, 44, 20, 20 },
    { 89, 44, 0, 20, 20 },
    { 90, 0, 1, 20, 20 },
    { 91, 1, 2, 20, 20 },
    { 92, 2, 3, 20, 20 },
    { 93, 3, 4, 20, 20 },
    { 94, 4, 5, 20, 20 },
    { 95, 5, 6, 20, 20 },
    { 96, 6, 7, 20, 20 },
    { 97, 7, 8, 20, 20 },
    { 98, 8, 9, 20, 20 },
    { 99, 9, 10, 20, 20 }
    };
    for (int j = 0; j < 100; j++)
        for (int i = 0; i < 5; i++) 
            demand[j][i] = dem[j][i];
}

int writeResult(char* fileName, int linesNumb, int poolSize)
{
    return 0;
}


// compare func for Qsort
int cmp(const void * pa, const void* pb) {
    const int(*a) = (const int*) pa;
    const int(*b) = (const int*) pb;
    // the last cell is the value to be sorted by (cost of a trip)
    if (*(a + MAX_IN_POOL + MAX_IN_POOL) < *(b+MAX_IN_POOL + MAX_IN_POOL)) return -1;
    if (*(a + MAX_IN_POOL + MAX_IN_POOL) > *(b+MAX_IN_POOL + MAX_IN_POOL)) return +1;
    return 0;
}

char* now() {
    struct tm newtime;
    __int64 ltime;
    char *buf = (char *)malloc(26);
    errno_t err;
    _time64(&ltime);
    err = _gmtime64_s(&newtime, &ltime);
    if (err)
    {
        printf("Invalid Argument to _gmtime64_s().");
    }
    err = asctime_s(buf, 26, &newtime);
    return buf;
}

char* now2() {
    struct tm newtime;
    __time32_t aclock;
        char *buffer = (char*)malloc(32);
        errno_t errNum;
        _time32(&aclock);   // Get time in seconds.
        _localtime32_s(&newtime, &aclock);   // Convert time to struct tm form.

        errNum = asctime_s(buffer, 32, &newtime);
        return buffer;
}

int drop_customers(int t, int pool_count, int** cost, int** demand, int** pool, int* pickup, int* dropoff, int level, int custInPool) {
    if (level == custInPool) { // we now know how to pick up and drop-off customers
        //count_all_all++;
        int happy = 1; // true
        for (int d = 0; d < custInPool; d++) { // checking if all 3(?) passengers get happy, starting from the one who gets dropped-off first
            int pool_cost = 0;
            // cost of pick-up phase TODO: maybe pickup phase should not be counted to 'happiness' ?
            for (int ph = dropoff[d]; ph < custInPool - 1; ph++)
                pool_cost += cost[demand[pickup[ph]][FROM]][demand[pickup[ph + 1]][FROM]];
            // cost of first drop-off
            pool_cost += cost[demand[pickup[custInPool - 1]][FROM]][demand[pickup[dropoff[0]]][TO]];
            // cost of drop-off
            for (int ph = 0; ph < d; ph++)
                pool_cost += cost[demand[pickup[dropoff[ph]]][TO]][demand[pickup[dropoff[ph + 1]]][TO]];
            if (pool_cost > cost[demand[pickup[dropoff[d]]][FROM]][demand[pickup[dropoff[d]]][TO]]
                * (1 + demand[pickup[dropoff[d]]][LOSS] / 100.0)) {
                happy = 0; // not happy
                break;
            }
        }
        if (happy) {
            // add to pool
            for (int i = 0; i < custInPool; i++) {
                pool[pool_count][i] = pickup[i];
                pool[pool_count][i + custInPool] = pickup[dropoff[i]];
            }
            int pool_cost = 0;
            for (int i = 0; i < custInPool - 1; i++) // cost of pick-up
                pool_cost += cost[demand[pickup[i]][FROM]][demand[pickup[i + 1]][FROM]];
            pool_cost += cost[demand[pickup[custInPool - 1]][FROM]][demand[pickup[dropoff[0]]][TO]]; // drop-off the first one
            for (int i = 0; i < custInPool - 1; i++) // cost of drop-off of the rest
                pool_cost += cost[demand[pickup[dropoff[i]]][TO]][demand[pickup[dropoff[i + 1]]][TO]];
            // that is an imortant decision - is this the 'goal' function to be optimized ?
            pool[pool_count][MAX_IN_POOL + MAX_IN_POOL] = pool_cost;
            pool_count++;
        }
    }
    else for (int c = 0; c < custInPool; c++) {
        // check if 'c' not in use in previous levels - "variation without repetition"
        int found = 0;
        for (int l = 0; l < level; l++) {
            if (dropoff[l] == c) {
                found = 1;
                break;
            };
        }
        if (found) continue; // next proposal please
        dropoff[level] = c;
        // a drop-off more
        pool_count = drop_customers(t, pool_count, cost, demand, pool, pickup, dropoff, level + 1, custInPool);
    }
    return pool_count;
}

int findPool(int t, int pool_count, int** cost, int** demand, int** pool, int* pickup, int* dropoff, int level, int numbCust, int start, int stop, int custInPool) { // level of recursion = place in the pick-up queue
    if (level == 0)
        printf("START: t=%d start=%d stop=%d\n", t, start, stop);
    if (level == custInPool) { // now we have all customers for a pool (proposal) and their order of pick-up
        // next is to generate combinations for the "drop-off" phase
        pool_count = drop_customers(t, pool_count, cost, demand, pool, pickup, dropoff, 0, custInPool);
    }
    else for (int c = start; c < stop; c++) {
        // check if 'c' not in use in previous levels - "variation without repetition"
        int found = 0;
        for (int l = 0; l < level; l++) {
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
            p_cost += cost[demand[pickup[l]][FROM]][demand[pickup[l + 1]][FROM]];
        if (p_cost > demand[pickup[level]][WAIT])
            continue;
        // find the next customer
        pool_count = findPool(t, pool_count, cost, demand, pool, pickup, dropoff, level + 1, numbCust, 0, numbCust, custInPool);
    }
    return pool_count;
}

int** allocateArr(int rows, int cols) {
    int** pool_ptr, * ptr;
    int len = sizeof(int*) * rows + sizeof(int) * cols * rows;
    pool_ptr = (int**)malloc(len);
    // first element in of the array
    ptr = (int*)(pool_ptr + rows);

    //  to point rows pointer to appropriate location in the array
    for (int i = 0; i < rows; i++)
        pool_ptr[i] = (ptr + cols * i);
    return pool_ptr;
}

void joinResults() {
    poolCountAll = 0;
    for (int t = 0; t < MAX_THREAD; t++) {
        for (int j = 0; j < poolCount[t]; j++, poolCountAll++)
            for (int i = 0; i < MAX_IN_POOL + MAX_IN_POOL + 1; i++)
                poolAll[poolCountAll][i] = poolPtr[t][j][i];
        free(poolPtr[t]);
    }
}


// https://w3.cs.jmu.edu/kirkpams/OpenCSF/Books/csf/html/ThreadArgs.html
void poolThread(int t)
{
    int len, col, * pickup_ptr, * dropoff_ptr, ** pool_ptr, * ptr, ** cost_ptr, ** demand_ptr;


    cost_ptr = allocateArr(n, n);
    setCosts(cost_ptr);

    demand_ptr = allocateArr(n, 5); // TODO: read from args, one read from disk for all threads
    readDemand(fileName, ordersCount, demand_ptr);

    pool_ptr = allocateArr(MAX_ARR, MAX_IN_POOL + MAX_IN_POOL + 1);

    len = sizeof(int) * MAX_IN_POOL;
    pickup_ptr = (int*)calloc(sizeof(int) , MAX_IN_POOL);
    dropoff_ptr = (int*)calloc(sizeof(int) , MAX_IN_POOL);

    int start = t * step;
    int stop = start + step > ordersCount ? ordersCount : start + step;

    poolCount[t] = findPool(t, 0, cost_ptr, demand_ptr, pool_ptr, pickup_ptr, dropoff_ptr, 0, ordersCount, start, stop, poolSize);
    poolPtr[t] = pool_ptr;

    free(pickup_ptr);
    free(dropoff_ptr);
    free(cost_ptr);
    free(demand_ptr);
    //pthread_exit(NULL);
}

void runThreads() {

    //for (int i=0; i<MAX_THREAD; i++)
    std::thread t1(poolThread, 0);
    std::thread t2(poolThread, 1);
    std::thread t3(poolThread, 2);
    std::thread t4(poolThread, 3);
    std::thread t5(poolThread, 4);
    std::thread t6(poolThread, 5);
    std::thread t7(poolThread, 6);
    std::thread t8(poolThread, 7);

    t1.join();
    t2.join();
    t3.join();
    t4.join();
    t5.join();
    t6.join();
    t7.join();
    t8.join();

    //pthread_create(&th1[i], NULL, poolThread, (void*)i);

  // wait for all threads to complete

  //for (int i=0; i<MAX_THREAD; i++)
//	pthread_join(th1[i], &status[i]);

  //copying results from all threads to one table - to be sorted
    joinResults();
}


void removeDuplicates() {
    qsort(poolAll, poolCountAll, sizeof poolAll[0], cmp);
    //printf("Removing duplicates ... %s\n", now());
    for (int i = 0; i < poolCountAll; i++) {
        if (poolAll[i][0] == -1) continue;
        for (int j = i + 1; j < poolCountAll; j++)
            if (poolAll[j][0] != -1) { // not invalidated; for performance reasons
                int found = 0; // false
                for (int x = 0; x < MAX_IN_POOL; x++) {
                    for (int y = 0; y < MAX_IN_POOL; y++)
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


int main(int argc, char* argv[]) {

  
    ordersCount = 100;
    poolSize = 4;

    //readDemand(fileName, ordersCount); // filling 'demand' table

    //printf("ordersCount: %d\n", ordersCount);
    printf("Start: %s\n", now());
    printf("Start2: %s\n", now2());

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
    printf("STOP: %s\n", now());
    printf("STOP2: %s\n", now2());

    //printf("Not duplicated count: %d\n", good_count);

}
