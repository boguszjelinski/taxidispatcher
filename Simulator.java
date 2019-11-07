package com.taxi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Simulator {
	final static short ID = 0;
	final static short FROM = 1;
	final static short TO = 2;
	final static short CLNT_ASSIGNED = 3;
	final static short CLNT_ON_BOARD = 4;
	final static short TIME_REQUESTED= 4;
	final static short CAB_ASSIGNED = 5;
	final static short TIME_STARTED = 5;
	final static short TIME_PICKUP = 7;
	
	final static short CAB = 0;
	final static short CLNT = 1;
	
	final static String PATH = "c:\\home\\dell\\";
	final static String DEMAND_FILE = PATH + "taxi_demand.txt"; // real input fro simulations
	final static String SOLVER_COST_FILE = PATH + "cost.txt"; // temp
	final static String SOLVER_OUT_FILE = PATH + "solv_out.txt"; // temp
	final static String SOLVER_CMD = "C:\\Python\\Python37\\python C:\\home\\dell\\solver.py";
	final static String OUTPUT_FILE = PATH + "simulog2.txt"; // history of dispatching
	final static String OUTPUT_ALGO_FILE = PATH + "simulog_solv2.txt"; // decisions/stats of the solver
			
	final static int hours = 2, 
			reqs_per_minute = 200, 
			n_stands = 50,
			DROP_TIME = 10, // minutes/iterations which make a customer stop thinking about the taxi
			MAX_NON_LCM = 501, // # max size of model put to solver
			n_cabs = 900,
			big_cost = 250000,
			max_model = 3000; // about 5000 for NYC
	static long demandCount=0;
	static int tempDemandCount, tempSupplyCount;
	
	/*
	 !!! "aside effect" (global variables) used to avoid costly stack operations. 
	 */

	static int[][] demand;
	static int[][] cabs = new int[n_cabs][6];
	static int[][] temp_supply = new int[n_cabs][3];
	static int[][] temp_demand = new int[max_model][3];
	static int[][] dist = new int[n_stands][n_stands];
	
	static int[] x = new int[max_model * max_model];
	static int[][] cost = new int[max_model][max_model];
	static int[][] costLCM = new int[max_model][max_model];
	static int[][] LCMpair = new int[max_model][2]; // supply idx, demand idx
	static int LCM_min_val ;
	
	// METRICS
	static int total_dropped = 0;
	static int total_pickup_time = 0;
	static int total_pickup_numb = 0;
	static int total_simul_time = 0;
	static int max_solver_time = 0;
	static int max_LCM_time = 0;
	static int total_LCM_used = 0;
	static int max_model_size = 0;
	static int max_solver_size = 0;
	/*
	 * avg trip duration
	 */
	
	public static void main(String[] args) throws IOException {
		FileWriter f = new FileWriter(OUTPUT_FILE);
		FileWriter f_solv = new FileWriter(OUTPUT_ALGO_FILE);
                        
		computeDistances();
		initSupply();
		//testStuff(f, f_solv);
		readDemand();
		long start = System.currentTimeMillis();
		
		for (int t=0; t<hours*60; t++) {
			System.out.println(t);
			 // checking if cabs have reached their destinations
		    for (int c=0; c<n_cabs; c++) 
			    if (cabs[c][FROM] != cabs[c][TO] 
			    		&& Math.abs(cabs[c][FROM]-cabs[c][TO]) == t-cabs[c][TIME_STARTED]) { // # the cab has gone as long as the distance
			        if (cabs[c][TIME_STARTED] == -1)
			            f.write("Time "+ t +". Error: Cab " + cabs[c][ID] + " goes from "
			            		 + cabs[c][FROM]+" to "+cabs[c][TO]+" and has no start time\n");
			        if (cabs[c][CLNT_ON_BOARD] == 0) { // the cab was empty, it was heading a customer assigned in c[3]
			             for (int d=0; d<demandCount; d++)
			                if (demand[d][ID] == cabs[c][CLNT_ASSIGNED]) { // # found the customer it was assigned to
			                	demand[d][CAB_ASSIGNED] = cabs[c][ID];
		                        demand[d][TIME_PICKUP] = t;
		                        
		                        f.write("Time "+t+". Customer "+ demand[d][ID] +
		                        		" picked up by Cab "+ cabs[c][ID] + "\n");
		                        total_pickup_numb++;
		                        // assign the cab to customer's trip
		                        cabs[c][FROM] = demand[d][FROM];
		                        cabs[c][TO] = demand[d][TO];
		                        cabs[c][CLNT_ASSIGNED] = demand[d][ID];
		                        cabs[c][CLNT_ON_BOARD] = 1; // true
		                        cabs[c][TIME_STARTED] = t;
		                        break;
			                }
			        }
			        else { // a trip has just been completed
		                cabs[c][FROM] = cabs[c][TO]; // "I am free!"
		                cabs[c][CLNT_ASSIGNED] = -1; // unassigned
		                cabs[c][CLNT_ON_BOARD] = 0; // no passenger on board;
		                cabs[c][TIME_STARTED] = -1; // no time started; maybe useful to count wasted time ?
		                f.write("Time "+t+". Cab "+ cabs[c][ID]+ " is free at stand "+cabs[c][TO] +"\n");
			        }
			    }
			 // create demand for the solver
	         tempDemandCount = createTempDemand(t,f);
		     if (tempDemandCount == 0) continue; // don't solve anything
			 tempSupplyCount = createTempSupply();
			 f_solv.write("\nt:"+t+". Initial Count of demand="+ tempDemandCount +", supply="+ tempSupplyCount+ ". ");
			 int n = 0;
					 
			 // SOLVER
			 if (tempSupplyCount>0) {
				 n = calculate_cost(tempDemandCount, tempSupplyCount);
				 if (n > max_model_size) max_model_size = n;
				 if (n > MAX_NON_LCM) {
					 costLCM = Arrays.stream(cost).map(int[]::clone).toArray(int[][]::new);
					 int n_pairs = 0;
					 long start_lcm = System.currentTimeMillis();
					 // LCM
					 n_pairs = LCM(n, f_solv);
					 total_LCM_used++;
					 long end_lcm = System.currentTimeMillis();
					 int temp_lcm_time = (int)((end_lcm - start_lcm) / 1000F);
					 if (temp_lcm_time > max_LCM_time) max_LCM_time = temp_lcm_time;
					 if (n_pairs == 0) {
						 // critical -> a big model but LCM hasn't helped 
					 }
					 f_solv.write("LCM n_pairs="+ n_pairs);
					 analyzePairs(t, n_pairs, f); // also produce input for the solver
					 
					 if (LCM_min_val == big_cost) // no input for the solver
						 continue;
					 
					 n = calculate_cost(tempDemandCount, tempSupplyCount);
					 f_solv.write(". Sent to solver: demand="+ tempDemandCount +", supply="+ tempSupplyCount+ ". ");
				 }
				 if (n > max_solver_size) max_solver_size = n;
				 Process p = Runtime.getRuntime().exec(SOLVER_CMD);
				 try {
					long start_solver = System.currentTimeMillis();
					p.waitFor();
					long end_solver = System.currentTimeMillis();
					int temp_solver_time = (int)((end_solver - start_solver) / 1000F);
					if (temp_solver_time > max_solver_time) max_solver_time = temp_solver_time;
				} catch (InterruptedException e) {
					e.printStackTrace();
					System.exit(0);
				}
			 }
			 readSolversResult(n);	
			 analyzeSolution(n, t, f, f_solv);
		}
		long end = System.currentTimeMillis();
		total_simul_time = (int)((end - start) / 1000F);
		printMetrics(f_solv);
		f.close();
		f_solv.close();
	}
	
	static void printMetrics(FileWriter fw) throws IOException {
		fw.write("\nTotal customers: " +demandCount);
		fw.write("\nTotal dropped customers: " +total_dropped);
		fw.write("\nTotal pickeup customers: " +total_pickup_numb);
		fw.write("\nTotal simulation time [secs]: " +total_simul_time);
		fw.write("\nTotal pickup time: " +total_pickup_time);
		if (total_pickup_numb>0)
			fw.write("\nAvg pickup time: " + total_pickup_time/total_pickup_numb);
		fw.write("\nMax model size: " +max_model_size);
		fw.write("\nMax solver size: " +max_solver_size);
		fw.write("\nMax solver time: " +max_solver_time);
		fw.write("\nMax LCM time: " +max_LCM_time);
		fw.write("\nLCM use count: " +total_LCM_used);
	}
	
	// the demand file describes customers requests throughout the whole simulation period
	private static void readDemand() {
		try {
			Path path = Path.of(DEMAND_FILE);
			demandCount = Files.lines(path).count();
			demand = new int[(int)demandCount][8];
			BufferedReader reader = new BufferedReader(new FileReader(DEMAND_FILE));
			String line = reader.readLine();
			int count=0;
		    while (line != null) {
		        if (line == null) break;
		        line = line.substring(1, line.length()-1); // get rid of '('
		        String[] lineVector = line.split(",");
		        for (int j=0; j<5; j++) // gendemand.py generates 5 values
		        	demand[count][j]=Integer.parseInt(lineVector[j]);
		        demand[count][CAB_ASSIGNED]= -1;
		        demand[count][TIME_PICKUP] = -1;		
		        count++;
		        line = reader.readLine();
		    }
		    reader.close();
		}
		catch (IOException e) {}
	}
	
	private static void readSolversResult(int nn) {
		try {
			BufferedReader reader = new BufferedReader(new FileReader(SOLVER_OUT_FILE));
			String line = null;
			for (int i=0; i<nn*nn; i++) {
		        line = reader.readLine();
		        if (line == null) {
					System.out.println("wrong output from solver");
					System.exit(0);
				}
		        x[i] = Integer.parseInt(line);
		    }
		    reader.close();
		}
		catch (IOException e) {
			System.out.println("missing output from solver");
			System.exit(0);
		}
	}

	// solver works on a limited number of customers, not the whole set read from file
	private static int createTempDemand(int t, FileWriter f) throws IOException {
         int count = 0;
		 for (int d=0; d<demandCount; d++)
// [3] is the time when the demand comes, [4] is time requested [3]>=[4]
// below is to simplistic, we should check [3] and send a cab to the customer
		    if (demand[d][CAB_ASSIGNED] == -1 && t >= demand[d][TIME_REQUESTED]) { // not assigned, not dropped (-2) and not earlier than requested
		       if (t-demand[d][TIME_REQUESTED] >= DROP_TIME) { // this customer will never be serviced, after 10 minutes of waiting the customer will look for something else
	                demand[d][CAB_ASSIGNED] = -2;
	                total_dropped++;
	                f.write("Time "+t+". Customer "+demand[d][ID]+" dropped\n");
		       }
		       else {
		    	   // checking if any cab is in 0..DROP_TIME range; as it is useless to build a model with such a customer, they will get big_cost anyway 
		    	   for (int c=0; c<n_cabs; c++)
		    		   if (cabs[c][CLNT_ASSIGNED] == -1 && 
		    		   		Math.abs(cabs[c][TO] - demand[d][FROM]) < DROP_TIME) { 
					    	   temp_demand[count][ID]   = demand[d][ID];
					    	   temp_demand[count][FROM] = demand[d][FROM];
					    	   temp_demand[count][TO]   = demand[d][TO];
					    	   count++;
					    	   break;
		    		   }
		       }
		    }
	     return count;
	}
	
	// not all cabs are sent to the solver, only free & unassigned
	private static int createTempSupply() throws IOException {
		 int count =0;
		 for (int c1=0; c1<n_cabs; c1++)
		     if (cabs[c1][1] == cabs[c1][2] && cabs[c1][3] == -1)  // any cab with passenger (maybe stupid to absorb the solver) or standing and not assigned
		            // or c[4]==1 without it we are ignoring cabs during a trip
		     {	 // checking if this cab is in range 0..DROP_TIME to any unassigned customer
		    	 for (int d=0; d<demandCount; d++)
		    		 if(demand[d][CAB_ASSIGNED] ==-1 &&
		    				 Math.abs(cabs[c1][TO] - demand[d][FROM]) < DROP_TIME) {
				    	 temp_supply[count][ID]   = cabs[c1][ID];
				    	 temp_supply[count][FROM] = cabs[c1][FROM];
				    	 temp_supply[count][TO]   = cabs[c1][TO];
				    	 count++;
				    	 break;
		    		 }
		     }
		 return count;
	}
	
	// solver returns a very simple output, it has to be compared with data which helped create its input 
	private static void analyzeSolution(int nn, int t, FileWriter f, FileWriter f_solv) throws IOException {
	  int total_count=0;
	  for(int s=0; s<nn; s++) 
		for(int c=0; c<nn; c++) 
		  if (x[nn*s+c]==1 && cost[s][c]< big_cost // not a fake assignment (to balance the model)
		                    // that is an unnecessary check as we commented out 'or c[4]==1 above
		        && temp_supply[s][FROM]==temp_supply[s][TO]) { // a cab with no assignment, no need to check [3]
			  total_count++;
		      // first log the assignment in the trip request
		      for (int d=0; d<demandCount; d++)
		          if (demand[d][ID] == temp_demand[c][ID]) { 
                     demand[d][CAB_ASSIGNED] = temp_supply[s][ID];
                     demand[d][TIME_PICKUP] = t;
                     break;
		          }
		          // assign the job to the cab
		      for (int c2=0; c2<n_cabs; c2++)
		        if (cabs[c2][ID] == temp_supply[s][ID]) { 
                  if (temp_supply[s][TO] == temp_demand[c][FROM])  // cab is already there
                	  assignToCabAndGo(t, c2, c, f, "OPT");
	              else if (Math.abs(temp_supply[s][TO] - temp_demand[c][FROM]) < DROP_TIME)  
                    // the check above is not needed, we check the distance in 'calculate_cost', but let us keep for any future change
                    // start the trip without a passenger, to pick up a passenger
                    goToPickupUp(t, c2, c, f, "OPT");
	              break;
		        }
		      break; // in this column there should not be any more x[]=='1'
		  }
	  f_solv.write("; OPT count="+total_count);
	}
	
	private static void assignToCabAndGo(int t, int s, int c, FileWriter f, String method) throws IOException {
		   cabs[s][FROM] 		  = temp_demand[c][FROM];
           cabs[s][TO] 			  = temp_demand[c][TO];
           cabs[s][CLNT_ASSIGNED] = temp_demand[c][ID];
           cabs[s][CLNT_ON_BOARD] = 1;
           cabs[s][TIME_STARTED]  = t;
           String str = "Time "+ t +". Customer "+ temp_demand[c][ID]
         		  + " assigned to and picked up by Cab "+ cabs[s][ID] + " (method " +method+ ")\n";
           total_pickup_numb++;
           f.write(str);
	}
	
	private static void goToPickupUp(int t, int s, int c, FileWriter f, String method) throws IOException {
        // unnnecessary ?? cabs[c2][1] = temp_supply[s][2]; // when assigned all cabs are free -> cabs[][1]==cabs[][2]
        cabs[s][TO] 		   = temp_demand[c][FROM];
        cabs[s][CLNT_ASSIGNED] = temp_demand[c][ID];
        cabs[s][CLNT_ON_BOARD] = 0; // it should already have that value 
        cabs[s][TIME_STARTED]  = t;
        String str ="Time "+ t +". Customer "+ temp_demand[c][ID] 
        		+" assigned to Cab "+ cabs[s][ID] +", cab is heading to the customer (method " +method+ ")\n"; 
        f.write(str);
        total_pickup_time += Math.abs(cabs[s][FROM] - cabs[s][TO]); // here we assume that all wait time is ASAP; but we should consider request "pick me up in 5 minutes"
        total_pickup_numb++;
	}
	
	// this routine preapers the cost matrix for solver or LCM 
	private static int calculate_cost (int n_demand, int n_supply) throws IOException {
	    int n = 0, c, d;
	    if (n_supply > n_demand) n = n_supply; // checking max size for unbalanced scenarios
	    else n = n_demand;
	    if (n==0) return 0;
	    // resetting cost table
	 	for (c=0; c<n; c++)
	 		for (d=0; d<n; d++) 
	 			cost[c][d] = big_cost;
	    for (c=0; c<n_supply; c++) 
	        for (d=0; d<n_demand; d++) 
	            if (dist [temp_supply[c][TO]] [temp_demand[d][FROM]] < DROP_TIME) // take this possibility only if reasonable time to pick-up a customer
	                // otherwise big_cost will stay in this cell
	                cost[c][d] = dist[temp_supply[c][TO]][temp_demand[d][FROM]];
	    FileWriter fr = new FileWriter(new File(SOLVER_COST_FILE));
	    fr.write( n +"\n");
	    for (c=0; c<n; c++) {
	        for (d=0; d<n; d++) fr.write(cost[c][d]+" ");
	        fr.write("\n");
	    }
	    fr.close();
	    return n;
	}
	
	// Low Cost Method aka "greedy" - looks for lowest values in the matrix
	private static int LCM(int n, FileWriter f_solv) throws IOException {
		int size=n, s, d, i;
		for (i=0; i<n; i++) { // we need to repeat the search (cut off rows/columns) 'n' times
			LCM_min_val = big_cost;
			int s_min = -1, d_min = -1; 
			for (s=0; s<n; s++)
				for (d=0; d<n; d++)
					if (costLCM[s][d] < LCM_min_val) {
						LCM_min_val = costLCM[s][d];
						s_min = s;
						d_min = d;
					}
			if (LCM_min_val == big_cost) break; // no more interesting stuff there
			LCMpair[i][CAB] = s_min;
			LCMpair[i][CLNT]= d_min;
			// removing the column from further search by assigning big cost
			for (s=0; s<n; s++) costLCM[s][d_min] = big_cost; 
			// the same with the row
			for (d=0; d<n; d++) costLCM[s_min][d] = big_cost;
			size--;
			if (size == MAX_NON_LCM) break; // rest will be covered by solver
		}
		return i; // number of allocated pairs
	}
	
	// initializes the distance database between stands
	// simplistic approach - distanse measured by diference in stand indices 
	private static void computeDistances() {
		for (int i=0; i<n_stands; i++)
		    for (int j=i; j<n_stands; j++) {
		        dist[j][i] = j-i; // simplification of distance - stop9 is closer to stop7 than to stop1
		        dist[i][j] = dist[j][i];
          	  // we have a 1D world and indexes of stands indicate the distance
		    }
	}
	
	private static void initSupply() {
		// we don't want random locations here as the simulation needs to be repeatable
		// supply has to be spread to make at least one cab be in DROP_TIME range; otherwise simulation will end with noeone transported 
		for (int i=0, j=0; i<n_cabs; i++, j++) {
			if (j>=n_stands) j=0;
			cabs[i][ID] 	= i; // maybe that cell is not necessary - index is ID
			cabs[i][FROM] 	= j;
			cabs[i][TO] 	= j; 
			cabs[i][CLNT_ASSIGNED] = -1;
			cabs[i][CLNT_ON_BOARD] = 0;
			cabs[i][TIME_STARTED]  = -1;
		}
	}
	
	private static void testStuff(FileWriter f, FileWriter f_solv) throws IOException {
		
		 int tempDemandCount = 10;
		 int tempSupplyCount = 9;
		 temp_demand[0][0]=0; temp_demand[0][1]=5; temp_demand[0][2]=7;
		 temp_demand[1][0]=1; temp_demand[1][1]=0; temp_demand[1][2]=2;
		 temp_demand[2][0]=2; temp_demand[2][1]=0; temp_demand[2][2]=9;
		 temp_demand[3][0]=3; temp_demand[3][1]=3; temp_demand[3][2]=1;
		 temp_demand[4][0]=4; temp_demand[4][1]=9; temp_demand[4][2]=2;
		 temp_demand[5][0]=5; temp_demand[5][1]=4; temp_demand[5][2]=5;
		 temp_demand[6][0]=6; temp_demand[6][1]=1; temp_demand[6][2]=6;
		 temp_demand[7][0]=7; temp_demand[7][1]=8; temp_demand[7][2]=2;
		 temp_demand[8][0]=8; temp_demand[8][1]=5; temp_demand[8][2]=6;
		 temp_demand[9][0]=9; temp_demand[9][1]=5; temp_demand[9][2]=0;
		 
		 for (int i=0; i<10; i++) {
				temp_supply[i][0] = i;
				temp_supply[i][1] = 5;
				temp_supply[i][2] = 5;
		}
		 
		 int n = calculate_cost(tempDemandCount, tempSupplyCount);
		 costLCM = Arrays.stream(cost).map(int[]::clone).toArray(int[][]::new);
		 LCM(n, f_solv);	 
		 
		 Process p = Runtime.getRuntime().exec(SOLVER_CMD);
		 try {
			p.waitFor();
		 } catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(0);
		 } 
		 readSolversResult(n);	
		 analyzeSolution(n, 0, f, f_solv);
		 f.close();
		 f_solv.close();
	}
	
	// the goal is 1) mark assignments 2) prepare input for the solver
	private static void analyzePairs(int t, int n_pairs, FileWriter f) throws IOException {
		int i, ii=0;
		int[][] temp_supply2 = new int[n_cabs][3]; 
		int[][] temp_demand2 = new int[max_model][3];
		
		for (int s=0; s<tempSupplyCount; s++) {
			for (i=0; i<n_pairs; i++)
				if (LCMpair[i][CAB] == s) {
					// we have found the cab (its ID!) in the set sent to LCM
					// now we have to find the cab in the original, big set
					for (int s2=0; s2<n_cabs; s2++) 
						if (temp_supply[s][ID] == cabs [s2][ID]) {
							if (temp_supply[s][TO] == temp_demand[LCMpair[i][CLNT]][FROM])  // cab is already there
							  assignToCabAndGo(t, s2, LCMpair[i][CLNT], f, "LCM");
							else if (Math.abs(temp_supply[s][TO] - temp_demand[LCMpair[i][CLNT]][FROM]) < DROP_TIME)  
							  // the check above is not needed, we check the distance in 'calculate_cost', but let us keep for any future change
							  // start the trip without a passenger, to pick up a passenger
							  goToPickupUp(t, s2, LCMpair[i][CLNT], f, "LCM");
							else {
								// should be dropped but we check it before, so this should not happen to navigate here
								System.out.print("Weird, this should not happen");
							}
							break;
						}
					break;
				}
			if (i == n_pairs) { // not found in output from LCM, must go to solver
				temp_supply2[ii][ID]  = temp_supply[s][ID];
				temp_supply2[ii][FROM]= temp_supply[s][FROM];
				temp_supply2[ii][TO]  = temp_supply[s][TO];
				ii++;
			}
		}
		tempSupplyCount = ii; // should equal 'tempSupplyCount-n_pairs'
		ii=0;
		for (int d=0; d<tempDemandCount; d++) { 
			for (i=0; i<n_pairs; i++)
				if (d == LCMpair[i][CLNT]) {
					for (int c2=0; c2<demandCount; c2++) 
						if (temp_demand[d][ID] == demand[c2][ID]) {
							demand[c2][CAB_ASSIGNED] = temp_supply[LCMpair[i][CAB]][ID];
		                    demand[c2][TIME_PICKUP] = t;
		                    break;
						}
					break;
				}
			if (i == n_pairs) { // not found
				temp_demand2[ii][ID]  = temp_demand[d][ID];
				temp_demand2[ii][FROM]= temp_demand[d][FROM];
				temp_demand2[ii][TO]  = temp_demand[d][TO];
				ii++;
			}
		}
		tempDemandCount = ii;
		temp_supply = Arrays.stream(temp_supply2).map(int[]::clone).toArray(int[][]::new);
		temp_demand = Arrays.stream(temp_demand2).map(int[]::clone).toArray(int[][]::new);
	}
}
