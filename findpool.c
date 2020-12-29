#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#define MAX_IN_POOL 4
#define MAX_THREAD 8
#define MAX_ARR 100000

int ordersCount;
int poolSize;

int pool[MAX_ARR][MAX_IN_POOL + MAX_IN_POOL + 1]; // pick-ups + dropp-offs + cost
int poolAll[MAX_ARR][MAX_IN_POOL + MAX_IN_POOL + 1];
int poolCountAll = 0;

void readOutput(char * fileName, int inPool)
{
    FILE * fp;
    char line[40];

    fp = fopen(fileName, "r");
    if (fp == NULL) {
        printf("Opening file %s failed\n", fileName);
        exit(EXIT_FAILURE);
    }
    memset (pool, -1, sizeof(pool));
    char* tok;
    int rec = 0;
    while (fgets(line, sizeof(line), fp)) {
        // five fields expected
        tok = strtok(line, ",");
        for (int i=0; i<inPool + inPool + 1; i++) { // pickup + dropoff + cost
            pool[rec][i] = atoi(tok);
            tok = strtok(NULL, ",");
            if (tok == NULL) break;
        }
        rec++;
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
    char *stamp = (char *)malloc(sizeof(char) * 20);
    time_t lt = time(NULL);
    struct tm *tm = localtime(&lt);
    sprintf(stamp,"%04d-%02d-%02d %02d:%02d:%02d", tm->tm_year+1900, tm->tm_mon+1, tm->tm_mday, tm->tm_hour, tm->tm_min, tm->tm_sec);
    return stamp;
}

void copyOutput() {
  for (int j=0; j<MAX_ARR && pool[j][0] != -1; j++, poolCountAll++)
    for (int i=0; i<MAX_IN_POOL + MAX_IN_POOL + 1; i++)
	  poolAll[poolCountAll][i] = pool[j][i];
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

int flagIsSet(int t) {
	char file[20];
	sprintf(file, "out%d.flg", t);
	if( access( file, F_OK ) == 0 ) {
	    // file exists
		return 1;
	} else {
	    // file doesn't exist
		return 0;
	}
	/*FILE * fp= fopen(file, "r");
	if (fp == NULL) return 0; // nope
	fclose(fp);
	return 1; // yup
	*/
}

int main(int argc, char *argv[])
{
	int outputRead[MAX_THREAD];
	char cmd[50], file[50];

    if (argc != 5) {
        printf("Usage: findpool pool-size demand-file-name rec-number output-file");
        exit(EXIT_FAILURE);
    }

    const char * fileName = argv[2];
    ordersCount = atoi(argv[3]);
    poolSize = atoi(argv[1]);

    printf("Start: %s\n", now());

    system("del *.flg out*.csv");
    for (int i=0; i<MAX_THREAD; i++) {
    	sprintf(cmd, "start /B pool_n %d %d %s %d out%d.csv &", poolSize, i, fileName, ordersCount, i);
    	system(cmd);
    }

    clock_t startTime =clock();
    int count=0;

    for (int i=0; i<MAX_THREAD; i++) outputRead[i] = 0;

    while(count<MAX_THREAD) {
      for (int i=0; i<MAX_THREAD && count<MAX_THREAD; i++)
    	if (outputRead[i] == 0 && flagIsSet(i)) {
    		printf("Reading thread %d\n", i);
    		count++;
    		outputRead[i] = 1; // don't check this thread any more
    		sprintf(file, "out%d.csv", i);
    		readOutput(file, poolSize);
    		copyOutput();
    		remove(file);
    		sprintf(file, "out%d.flg", i);
    		remove(file);
    	}
      usleep(10);
      if (( (double) clock() - startTime)/CLOCKS_PER_SEC > 60)
		 break;
    }
    //printf("Sorting ... %s\n", now());
    if (count != MAX_THREAD) {
    	printf("ERROR: not all threads have returned results");
    	exit(EXIT_FAILURE);
    }
    removeDuplicates();
    int good_count = 0;
    good_count = writeResult(argv[4], poolCountAll, poolSize);

    //printf("Not duplicated count: %d\n", good_count);
    printf("Stop: %s\n", now());
}
