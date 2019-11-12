package com.taxi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Simulator {
	
	private static class Pool implements Comparable<Pool> {
	    public int custA;
	    public int custB;
	    public int plan;
	    public int cost;
	 
	    Pool () {}
	    
	    @Override
	    public int compareTo(Pool pool) {
	        return this.cost - pool.cost;
	    }
	}
		
	private static class Supply {
		int id, from, to;

		public Supply(int id, int from, int to) {
			super();
			this.id = id;
			this.from = from;
			this.to = to;
		}
	}
	
	private static class Demand {
		int id, from, to, pool_clnt_id, pool_plan, pool_cost;

		public Demand(int id, int from, int to, int pool_clnt_id, int pool_plan, int pool_cost) {
			this.id = id;
			this.from = from;
			this.to = to;
			this.pool_clnt_id = pool_clnt_id;
			this.pool_plan = pool_plan;
			this.pool_cost = pool_cost;
		}
	}
	
	private static class TempModel {
		public Supply[] supply;
		public Demand[] demand;
		
		public TempModel(Supply[] supply, Demand[] demand) {
			this.supply = supply;
			this.demand = demand;
		}
	}
	
	private static class LcmPair {
		int cab, clnt;

		public LcmPair(int cab, int clnt) {
			this.cab = cab;
			this.clnt = clnt;
		}
	}
	
	final static short ID = 0;
	final static short FROM = 1;
	final static short TO = 2;
	final static short CLNT_ASSIGNED = 3;
	final static short CLNT_ON_BOARD = 4; // cab
	final static short TIME_REQUESTED= 4; // demand 
	final static short CAB_ASSIGNED = 5; // demand
	final static short TIME_STARTED = 5; // cab, to count when it will reach the customer
	final static short TIME_PICKUP = 7;
	
	final static short CLNT_A_ENDS = 0;
	final static short CLNT_B_ENDS = 1;
	
	final static String PATH = "c:\\home\\dell\\";
	final static String DEMAND_FILE = PATH + "taxi_demand.txt"; // real input fro simulations
	final static String SOLVER_COST_FILE = PATH + "cost.txt"; // temp
	final static String SOLVER_OUT_FILE = PATH + "solv_out.txt"; // temp
	final static String SOLVER_CMD = "C:\\Python\\Python37\\python C:\\home\\dell\\solver.py";
	final static String OUTPUT_FILE = PATH + "simulog.txt"; // history of dispatching
	final static String OUTPUT_ALGO_FILE = PATH + "simulog_solv.txt"; // decisions/stats of the solver
			
	final static int hours = 2, 
			reqs_per_minute = 400, 
			n_stands = 50,
			DROP_TIME = 10, // minutes/iterations which make a customer unsatisfied
			MAX_NON_LCM = 600, // # max size of model put to solver
			n_cabs = 1300,
			big_cost = 250000;  
	final static double max_loss = 1.01; // pool customers accept only 1% loss of time if driving collectively 
	static long demandCount=0;
	static int[][] demand;
	static int[][] cabs = new int[n_cabs][6];
	static int[][] dist = new int[n_stands][n_stands];

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
	static int max_POOL_time = 0;
	static int max_POOL_MEM_size = 0;
	static int max_POOL_size = 0;
	static int total_second_passengers = 0;
	/*
	 * avg trip duration
	 */
	
	public static void main(String[] args) throws IOException {
		FileWriter f = new FileWriter(OUTPUT_FILE);
		FileWriter f_solv = new FileWriter(OUTPUT_ALGO_FILE);
                        
		computeDistances();
		initSupply();
	
		readDemand();
		long start = System.currentTimeMillis();
		
		for (int t=0; t<hours*60; t++) {
			System.out.println(t);
			 // checking if cabs have reached their destinations - both to a customer or with a customer
			 // and make modifications to 'demand' & 'cabs'
			 checkIfCabAtDestination(t, f);
		     
			 // create demand for the solver
	         Demand[] temp_demand = createTempDemand(t,f);
		     if (temp_demand.length == 0) continue; // don't solve anything
			 Supply[] temp_supply = createTempSupply();
			 f_solv.write("\nt:"+t+". Initial Count of demand="+ temp_demand.length +", supply="+ temp_supply.length + ". ");
			 //int n = 0;
			 int[][] cost = new int[0][0];
			 // SOLVER
			 if (temp_supply.length > 0) {
				 
				 Pool[] pl = findPool(t, f, temp_demand);
				 temp_demand = analyzePool(pl, temp_demand); // reduce temp_demand
				 
				 cost = calculate_cost(temp_demand, temp_supply);
				 if (cost.length > max_model_size) max_model_size = cost.length;
				 if (cost.length > MAX_NON_LCM) { // too big to send to solver, it has to be cut by LCM 
					 long start_lcm = System.currentTimeMillis();
					 // LCM
					 LcmPair[] pairs = LCM(cost, f_solv);
					 total_LCM_used++;
					 long end_lcm = System.currentTimeMillis();
					 int temp_lcm_time = (int)((end_lcm - start_lcm) / 1000F);
					 if (temp_lcm_time > max_LCM_time) max_LCM_time = temp_lcm_time;
					 if (pairs.length == 0) {
						 System.out.println ("critical -> a big model but LCM hasn't helped");
					 }
					 f_solv.write("LCM n_pairs="+ pairs.length);
					 TempModel tempModel = analyzePairs(t, pairs, f, temp_demand, temp_supply); // also produce input for the solver
					 temp_supply = tempModel.supply;
					 temp_demand = tempModel.demand;
					 
					 if (LCM_min_val == big_cost) // no input for the solver; 
						 continue;
					 
					 cost = calculate_cost(temp_demand, temp_supply);
					 f_solv.write(". Sent to solver: demand="+ temp_demand.length +", supply="+ temp_supply.length+ ". ");
				 }
				 if (cost.length > max_solver_size) max_solver_size = cost.length;
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
			 int[] x = readSolversResult(cost.length);	
			 analyzeSolution(x, cost, t, f, f_solv, temp_demand, temp_supply);
			 // now we could check if a customer in pool has got a cab, 
			 // if not - the other one should get a chance
			 f.flush();
		}
		long end = System.currentTimeMillis();
		total_simul_time = (int)((end - start) / 1000F);
		printMetrics(f_solv);
		f.close();
		f_solv.close();
	}
	
	private static void checkIfCabAtDestination(int t, FileWriter f) throws IOException {
	  for (int c=0; c<n_cabs; c++) 
		if (cabs[c][FROM] != cabs[c][TO] 
		    		&& dist[cabs[c][FROM]][cabs[c][TO]] == t-cabs[c][TIME_STARTED]) { // # the cab has gone as long as the distance
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
	}
	
	static void printMetrics(FileWriter fw) throws IOException {
		fw.write("\nTotal customers: " +demandCount);
		fw.write("\nTotal dropped customers: " +total_dropped);
		fw.write("\nTotal pickedup customers: " +total_pickup_numb);
		int count=0;
		for (int c=0; c<demandCount; c++)
			if (demand[c][CAB_ASSIGNED]>-1) count++;
		fw.write("\nTotal customers with assigned cabs: " +count);
		fw.write("\nTotal simulation time [secs]: " +total_simul_time);
		fw.write("\nTotal pickup time: " +total_pickup_time);
		if (total_pickup_numb>0)
			fw.write("\nAvg pickup time: " + total_pickup_time/total_pickup_numb);
		fw.write("\nMax model size: " +max_model_size);
		fw.write("\nMax solver size: " +max_solver_size);
		fw.write("\nMax solver time: " +max_solver_time);
		fw.write("\nMax LCM time: " +max_LCM_time);
		fw.write("\nLCM use count: " +total_LCM_used);
		fw.write("\nMax POOL time: " +max_POOL_time);
		fw.write("\nMax POOL array size: " +max_POOL_MEM_size);
		fw.write("\nMax POOL size: " +max_POOL_size);
		fw.write("\nTotal second customers in POOL: " +total_second_passengers);
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
	
	private static int[] readSolversResult(int nn) {
		int[] x = new int[nn * nn];
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
		return x;
	}

	// solver works on a limited number of customers, not the whole set read from file
	private static Demand[] createTempDemand(int t, FileWriter f) throws IOException {
		List<Demand> list = new ArrayList<Demand>();
		 String str="Time "+t+". tempDemand: ";
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
		    		   		dist[cabs[c][TO]][demand[d][FROM]] < DROP_TIME) {
	    			   		   list.add(new Demand(demand[d][ID], demand[d][FROM], demand[d][TO], -1, -1, 0));
					    	   str += demand[d][ID] +", ";
					    	   break;
		    		   }
		       }
		    }
		 f.write(str+"\n");
		 f.flush();
	     return list.toArray(new Demand[list.size()]);
	}
	
	// not all cabs are sent to the solver, only free & unassigned
	private static Supply[] createTempSupply() throws IOException {
		 List<Supply> list = new ArrayList<Supply>();
		 for (int c1=0; c1<n_cabs; c1++)
		     if (cabs[c1][1] == cabs[c1][2] && cabs[c1][3] == -1)  // any cab with passenger (maybe stupid to absorb the solver) or standing and not assigned
		            // or c[4]==1 without it we are ignoring cabs during a trip
		     {	 // checking if this cab is in range 0..DROP_TIME to any unassigned customer
		    	 for (int d=0; d<demandCount; d++)
		    		 if(demand[d][CAB_ASSIGNED] == -1 &&
		    				 dist[cabs[c1][TO]][demand[d][FROM]] < DROP_TIME) {
		    			 list.add(new Supply(cabs[c1][ID], cabs[c1][FROM], cabs[c1][TO]));
				    	 break;
		    		 }
		     }
		 return list.toArray(new Supply[list.size()]);
	}
	
	// solver returns a very simple output, it has to be compared with data which helped create its input 
	private static void analyzeSolution(int[] x, int[][] cost, int t, FileWriter f, FileWriter f_solv, Demand[] temp_demand, Supply[] temp_supply) throws IOException {
	  int nn = cost.length;
	  int total_count=0;
	  for(int s=0; s < temp_supply.length; s++) 
		for(int c=0; c < temp_demand.length; c++) 
		  if (x[nn*s+c]==1 && cost[s][c]< big_cost // not a fake assignment (to balance the model)
		                    // that is an unnecessary check as we commented out 'or c[4]==1 above
		        && temp_supply[s].from == temp_supply[s].to) { // a cab with no assignment, no need to check [3]
			  total_count++;
		      // first log the assignment in the customer's request
		      for (int d=0; d<demandCount; d++)
		          if (demand[d][ID] == temp_demand[c].id) { 
                     demand[d][CAB_ASSIGNED] = temp_supply[s].id;
                     demand[d][TIME_PICKUP] = t;
                     // don't forget the other one in the pool!
                     if (temp_demand[c].pool_clnt_id > -1) {
                    	assignPooledCustomer(t, temp_demand[c].pool_clnt_id, temp_supply[s].id, "OPT", f);
                  	  	total_pickup_numb++;
                     }
                     break;
		          }
		      // assign the job to the cab
		      for (int c2=0; c2<n_cabs; c2++)
		        if (cabs[c2][ID] == temp_supply[s].id) { 
                  if (temp_supply[s].to == temp_demand[c].from)  // cab is already there
                	  assignToCabAndGo(t, c2, f, "OPT", temp_demand[c]);
	              else if (dist[temp_supply[s].to][temp_demand[c].from] < DROP_TIME)  
                    // the check above is not needed, we check the distance in 'calculate_cost', but let us keep for any future change
                    // start the trip without a passenger, to pick up a passenger
                    goToPickupUp(t, c2, f, "OPT", temp_demand[c]);
	              break;
		        }
		      break; // in this column there should not be any more x[]=='1'
		  }
	  f_solv.write("; OPT count="+total_count);
	  f.flush();
	  f_solv.flush();
	}
	
	private static void assignPooledCustomer(int t, int customer, int cab, String method, FileWriter f) throws IOException {
		if (customer < 0 || cab==-1) {
			System.out.println("assignPooledCustomer: wrong parameters");
			System.exit(0);
		}
		for (int d2=0; d2<demandCount; d2++) // looking for the guy in original demand
			if (demand[d2][ID] == customer) {
				demand[d2][CAB_ASSIGNED] = cab;
				String str = "Time "+ t +". Customer "+ customer
						+ " assigned in a pool as second passenger to Cab "+ cab + " (method "+method+")\n";
				f.write(str);
				total_second_passengers++;
				// TODO
				// here we could analyze which of two plans it is and assign pickup time
				break;
			}
		f.flush();
	}

	private static void assignToCabAndGo(int t, int s, FileWriter f, String method, Demand temp_demand) throws IOException {
	   if (temp_demand.id == -1) {
		   System.out.println("Error - temp_demand ID is -1");
		   System.exit(0);
	   }
	   String str = "Time "+ t +". Customer "+ temp_demand.id
   		  + " assigned to and picked up by Cab "+ cabs[s][ID];
	   cabs[s][FROM] = temp_demand.from;
	   if (temp_demand.pool_clnt_id == -1) // no pool
		   cabs[s][TO] = temp_demand.to;
	   else {
		   str += " (POOL: the other Customer "+ temp_demand.pool_clnt_id +")";
		   // now we wil cheat a bit, but the main goal is to keep the cab busy due to the real distance, 
		   // not to be so very precise with the endpoint
		   if (cabs[s][FROM] + temp_demand.pool_cost >= n_stands ) {
			   if (cabs[s][FROM] - temp_demand.pool_cost < 0)
				   cabs[s][TO] = 0;
			   else cabs[s][TO] = cabs[s][FROM] - temp_demand.pool_cost;
		   }
		   else cabs[s][TO] = cabs[s][FROM] + temp_demand.pool_cost;
	   }
       cabs[s][CLNT_ASSIGNED] = temp_demand.id;
       cabs[s][CLNT_ON_BOARD] = 1;
       cabs[s][TIME_STARTED]  = t;
       
       str += " (method " +method+ ")\n";
       total_pickup_numb++;
       f.write(str);
       f.flush();
	}
	
	private static void goToPickupUp(int t, int s, FileWriter f, String method, Demand temp_demand) throws IOException {
        // unnecessary ?? cabs[c2][1] = temp_supply[s][2]; // when assigned all cabs are free -> cabs[][1]==cabs[][2]
		if (temp_demand.id == -1) {
			   System.out.println("Error - temp_demand ID is -1");
			   System.exit(0);
		}
        cabs[s][TO] 		   = temp_demand.from;
        cabs[s][CLNT_ASSIGNED] = temp_demand.id;
        cabs[s][CLNT_ON_BOARD] = 0; // it should already have that value 
        cabs[s][TIME_STARTED]  = t;
        String str ="Time "+ t +". Customer "+ temp_demand.id 
        		+" assigned to Cab "+ cabs[s][ID] +", cab is heading to the customer (method " +method+ ")\n"; 
        f.write(str);
        total_pickup_time += dist[cabs[s][FROM]][cabs[s][TO]]; // here we assume that all wait time is ASAP; but we should consider request "pick me up in 5 minutes"
	}
	
	// this routine prepares the cost matrix for solver or LCM 
	private static int[][] calculate_cost (Demand[] temp_demand, Supply[] temp_supply) throws IOException {
	    int n = 0, c, d;
	    int n_supply = temp_supply.length;
	    int n_demand = temp_demand.length;
	    if (n_supply > n_demand) n = n_supply; // checking max size for unbalanced scenarios
	    else n = n_demand;
	    if (n==0) return new int[0][0];
	    
	    int[][] cost = new int[n][n];
	    // resetting cost table
	 	for (c=0; c<n; c++)
	 		for (d=0; d<n; d++) 
	 			cost[c][d] = big_cost;
	    for (c=0; c<n_supply; c++) 
	        for (d=0; d<n_demand; d++) 
	            if (temp_supply[c].id != -1 && temp_demand[d].id != -1 &&
	            		dist[temp_supply[c].to][temp_demand[d].from] < DROP_TIME) // take this possibility only if reasonable time to pick-up a customer
	                // otherwise big_cost will stay in this cell
	                cost[c][d] = dist[temp_supply[c].to][temp_demand[d].from];
	    FileWriter fr = new FileWriter(new File(SOLVER_COST_FILE));
	    fr.write( n +"\n");
	    for (c=0; c<n; c++) {
	        for (d=0; d<n; d++) fr.write(cost[c][d]+" ");
	        fr.write("\n");
	    }
	    fr.close();
	    return cost;
	}
	
	// Low Cost Method aka "greedy" - looks for lowest values in the matrix
	private static LcmPair[] LCM(int[][] cost, FileWriter f_solv) throws IOException {
		int n = cost.length;
		int[][] costLCM = Arrays.stream(cost).map(int[]::clone).toArray(int[][]::new);
		List<LcmPair> pairs = new ArrayList<LcmPair>();
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
			pairs.add(new LcmPair(s_min, d_min));
			// removing the column from further search by assigning big cost
			for (s=0; s<n; s++) costLCM[s][d_min] = big_cost; 
			// the same with the row
			for (d=0; d<n; d++) costLCM[s_min][d] = big_cost;
			size--;
			if (size == MAX_NON_LCM) break; // rest will be covered by solver
		}
		f_solv.flush();
		return pairs.toArray(new LcmPair[pairs.size()]); 
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
		 List<Supply> temp_supply = new ArrayList<Supply>();
		 List<Demand> temp_demand = new ArrayList<Demand>();
		 temp_demand.add(new Demand (0,5,7,-1,-1,0));
		 temp_demand.add(new Demand (1,0,2,-1,-1,0));
		 temp_demand.add(new Demand (2,0,9,-1,-1,0));
		 temp_demand.add(new Demand (3,3,1,-1,-1,0));
		 temp_demand.add(new Demand (4,9,2,-1,-1,0));
		 temp_demand.add(new Demand (5,4,5,-1,-1,0));
		 temp_demand.add(new Demand (6,1,6,-1,-1,0));
		 temp_demand.add(new Demand (7,8,2,-1,-1,0));
		 temp_demand.add(new Demand (8,5,6,-1,-1,0));
		 temp_demand.add(new Demand (9,5,0,-1,-1,0));
		 
		 for (int i=0; i<10; i++) 
			 	temp_supply.add(new Supply(i,5,5));
		 
		 int[][] cost = calculate_cost(temp_demand.toArray(new Demand[temp_demand.size()]),
				 				temp_supply.toArray(new Supply[temp_supply.size()]));
		 
		 LCM(cost, f_solv);	 
		 
		 Process p = Runtime.getRuntime().exec(SOLVER_CMD);
		 try {
			p.waitFor();
		 } catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(0);
		 } 
		 int[] x = readSolversResult(cost.length);	
		 analyzeSolution(x,cost, 0, f, f_solv, temp_demand.toArray(new Demand[temp_demand.size()]), temp_supply.toArray(new Supply[temp_supply.size()]));
		 f.close();
		 f_solv.close();
	}
	
	// the goal is 1) mark assignments 2) prepare input for the solver
	private static TempModel analyzePairs(int t, LcmPair[] pairs, FileWriter f, Demand[] temp_demand, Supply[] temp_supply) throws IOException {
		int i;
		List<Supply> temp_supply2 = new ArrayList<Supply>();
		List<Demand> temp_demand2 = new ArrayList<Demand>();
		
		for (int s=0; s < temp_supply.length; s++) 
		  if (temp_supply[s].id != -1) {
			for (i=0; i<pairs.length; i++)
				if (pairs[i].cab == s) {
					// we have found the cab (its ID!) in the set sent to LCM
					// now we have to find the cab in the original, big set
					for (int s2=0; s2<n_cabs; s2++) 
						if (temp_supply[s].id == cabs [s2][ID]) {
							Demand cust = temp_demand[pairs[i].clnt];
							if (temp_supply[s].to == cust.from)  // cab is already there
							  assignToCabAndGo(t, s2, f, "LCM", cust);
							else if (dist[temp_supply[s].to][cust.from] < DROP_TIME)  
							  // the check above is not needed, we check the distance in 'calculate_cost', but let us keep for any future change
							  // start the trip without a passenger, to pick up a passenger
							  goToPickupUp(t, s2, f, "LCM", cust);
							else {
								// should be dropped but we check it before, so this should not happen to navigate here
								System.out.print("Weird, this should not happen");
							}
							break;
						}
					break;
				}
			if (i == pairs.length) { // not found in output from LCM, must go to solver
				temp_supply2.add(new Supply(temp_supply[s].id, temp_supply[s].from, temp_supply[s].to));
			}
		}
		
		int ii=0;
		for (int d=0; d<temp_demand.length; d++) 
		  if (temp_demand[d].id != -1) { 
			for (i=0; i<pairs.length; i++)
				if (d == pairs[i].clnt) {
					for (int c2=0; c2<demandCount; c2++) 
						if (temp_demand[d].id == demand[c2][ID]) {
							demand[c2][CAB_ASSIGNED] = temp_supply[pairs[i].cab].id;
							f.write("Time: "+t+". Customer "+ demand[c2][ID] + " assigned by LCM to Cab "
										+demand[c2][CAB_ASSIGNED]+"\n");
		                    demand[c2][TIME_PICKUP] = t;
		                    // take care of the other customer in POOL
		                    if (temp_demand[d].pool_clnt_id > -1) {
		                    	assignPooledCustomer(t, temp_demand[d].pool_clnt_id, temp_supply[pairs[i].cab].id, "LCM", f);
		                  	  	total_pickup_numb++;
		                    }
		                    break;
						}
					break;
				}
			if (i == pairs.length) { // not found
				temp_demand2.add(new Demand (temp_demand[d].id, temp_demand[d].from, temp_demand[d].to, -1, -1, 0));
			}
		}
		f.flush();
		return new TempModel (	temp_supply2.toArray(new Supply[temp_supply2.size()]), 
								temp_demand2.toArray(new Demand[temp_demand2.size()]));
	}
	
	/* 1) find customers which will be the second one, custB - picked up by custA 
	 * 2) custB should be removed from temp_demand sent to solver
	 * 3) solver should find a cab for custA
	 * 4) if custA not assigned to a cab -> rerun solver for all custB
	 */
	private static Pool[] findPool (int t, FileWriter f, Demand[] temp_demand) throws IOException {
		int pool_numb=0;
	    List<Pool> poolList = new ArrayList<Pool>();
	    
		long start = System.currentTimeMillis();
		for (int custA=0; custA<temp_demand.length; custA++)
		  for (int custB=0; custB<temp_demand.length; custB++)
		     if (temp_demand[custA].id != -1 && temp_demand[custB].id != -1 && custA != custB) {
	            // 3 values in 2 plans: pick up B; go to A's destination, then go to B's destination OR
	            // pick up B; go to B's destination, then go to A's destination
	            boolean plan1=true, plan2=true; // false

	            int cost1 = dist[temp_demand[custA].from][temp_demand[custB].from] + // diff of index is distance
	                    	dist[temp_demand[custB].from][temp_demand[custA].to] +
	                    	dist[temp_demand[custA].to][temp_demand[custB].to];
	            
	            int cost2 = dist[temp_demand[custA].from][temp_demand[custB].from] +
	                    	dist[temp_demand[custB].from][temp_demand[custB].to] +
	                    	dist[temp_demand[custB].to][temp_demand[custA].to];
	            
	            if (dist[temp_demand[custB].from][temp_demand[custA].to] + dist[temp_demand[custA].to][temp_demand[custB].to]
	                < dist[temp_demand[custB].from][temp_demand[custB].to] * max_loss && // cust B does not lose much with it
	                  dist[temp_demand[custA].from][temp_demand[custB].from] + dist[temp_demand[custB].from][temp_demand[custA].to]
	                < dist[temp_demand[custA].from][temp_demand[custA].to] * max_loss // cust A does not lose much with it either
	                ) plan1 = true; //True
	            if (cost2 < dist[temp_demand[custA].from][temp_demand[custA].to] * max_loss) // cust A does not lose much with it, B never loses in this scenario
	                plan2 = true; //True
	            if (plan1 || plan2) {
	            	Pool pool = new Pool();
	                if (cost1 < cost2) { // plan1 is better
	                	 pool.plan = CLNT_B_ENDS;
	                	 pool.cost = cost1;
	                }
	                else {
	                	pool.plan = CLNT_A_ENDS;
	                	pool.cost = cost2;
	                }
	                pool.custA = custA;
	                pool.custB = custB;
	                poolList.add(pool);
	                pool_numb++;
	            }
	        }
	     // now we have to remove plans with same passengers
		 // sorting first
		 Pool[] arr = poolList.toArray(new Pool[poolList.size()]);
		 Arrays.sort(arr);
		 
		 for (int i=1; i<arr.length; i++) { // i does not start with 0 as [0] is the best plan in the set
	        for (int j=0; j<i; j++)
	            if (arr[j].custA != -1 && // do not compare dropped plans, waste of time
	            	(arr[i].custA == arr[j].custA || arr[i].custB == arr[j].custB ||
	                 arr[i].custA == arr[j].custB || arr[i].custB == arr[j].custA)) { // the 'i' plan shares one participant with
	                    //print ("Deleting (%d,%d) as (%d,%d) has this passenger " % (arr[i].custA, arr[i][1],arr[j].custA, arr[j][1]))
	                    // list is sorted; i>j => delete 'i'
	                arr[i].custA = -1; // mark as 'not to be considered'
	                break;
	            }
		 }
		 List<Pool> out = new ArrayList<Pool>();
		 
		 String str ="Time "+t+". Customers in pool: ";
		 for (int i=0; i<arr.length; i++)
		   	if (arr[i].custA != -1) { // do not show dropped plans
		   		out.add(arr[i]);
		   		str += temp_demand[arr[i].custA].id +"("+ temp_demand[arr[i].custB].id +"), ";
		   	}
		 if (out.size()>max_POOL_size) max_POOL_size = out.size();
		 
		 long end = System.currentTimeMillis();
		 int time = (int)((end - start) / 1000F);
		 if (time>max_POOL_time) max_POOL_time = time;
		 
		 if (pool_numb>max_POOL_MEM_size) max_POOL_MEM_size = pool_numb;
		 
		 f.write(str + "\n");
		 return out.toArray(new Pool[out.size()]); 
	}

    private static Demand[] analyzePool (Pool[] arr, Demand[] temp_demand) {
	  List<Demand> temp_demand2 = new ArrayList<Demand>();
	  
	  for (int d=0; d < temp_demand.length; d++) {
		 boolean found = false;
		 for (Pool a : arr)
			  if (a.custB == d) { // this customer will be picked up by another customer should not be sent tol solver 
				  found = true;
				  break;
			  }
		 if (!found) { // 'd' is not custB, it is custA and therefore will be sent to solver
			Demand cust = new Demand(temp_demand[d].id, temp_demand[d].from, temp_demand[d].to, -1, -1, 0);
			// ok, it is not the custB, but if it is custA that has to pick up custB, then we have to assign the custB
			for (Pool p : arr)
			  if (p.custA == d) {  
				  cust.pool_clnt_id = temp_demand[p.custB].id;
				  cust.pool_plan = p.plan; // custA begins but who will end first ?
				  cust.pool_cost = p.cost;
				  break;
			  }
			temp_demand2.add(cust);
		 }
	  }
	  return temp_demand2.toArray(new Demand[temp_demand2.size()]);
  }
}
