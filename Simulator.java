package com.taxi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/*
 !!! I use "aside effect"(global variables) to avoid stack operations. Sorry!
 
 */
public class Simulator {
	final static String PATH = "c:\\home\\dell\\";
	final static String DEMAND_FILE = PATH + "taxi_demand.txt"; // real input fro simulations
	final static String SOLVER_COST_FILE = PATH + "cost.txt"; // temp
	final static String SOLVER_OUT_FILE = PATH + "solv_out.txt"; // temp
	final static String SOLVER_CMD = "C:\\Python\\Python37\\python C:\\home\\dell\\solver.py";
	final static String OUTPUT_FILE = PATH + "simulog2.txt"; // history of dispatching
	final static String OUTPUT_ALGO_FILE = PATH + "simulog_solv2.txt"; // decisions/stats of the solver
			
	final static int hours = 2, 
			reqs_per_minute = 100, 
			n_stands = 50,
			DROP_TIME = 10,
			THRESHOLD = 20, // stands - max distance for LCM
			MAX_OPT = 200, // # max size of model put to solver
			n_cabs = 10,
			big_cost = 250000,
			max_model = 3000; // about 5000 for NYC
	static long demandCount=0;
	static int[][] demand;
	static int[][] cabs = new int[n_cabs][6];
	static int[][] temp_supply = new int[n_cabs][3];
	static int[][] temp_demand = new int[max_model][3];
	static int[][] dist = new int[n_stands][n_stands];
	
	static int[] x = new int[max_model * max_model];
	static int[][] cost = new int[max_model][max_model];
	static int[][] costLCM = new int[max_model][max_model];
	static int[][] LCMpair = new int[max_model][2]; // supply idx, demand idx
	
	public static void main(String[] args) throws IOException {
		FileWriter f = new FileWriter(OUTPUT_FILE);
		FileWriter f_solv = new FileWriter(OUTPUT_ALGO_FILE);
                        
		computeDistances();
		initSupply();
		//testStuff(f, f_solv);
		readDemand();
	
		for (int t=0; t<hours*60; t++) {
			 // checking if cabs have reached their destinations
		    for (int c=0; c<n_cabs; c++) 
			    if (cabs[c][1] != cabs[c][2] && Math.abs(cabs[c][1]-cabs[c][2]) == t-cabs[c][5]) { // # the cab has gone as long as the distance
			        if (cabs[c][5] == -1)
			            f.write("Time "+ t +". Error: Cab " + cabs[c][0] + " goes from "
			            		 + cabs[c][1]+" to "+cabs[c][1]+" and has no start time\n");
			        if (cabs[c][4] == 0) { // the cab was empty, it was heading a customer assigned in c[3]
			             for (int d=0; d<demandCount; d++)
			                if (demand[d][0] == cabs[c][3]) { // # found the customer
			                	demand[d][5] = cabs[c][0];
		                        demand[d][7] = t;
		                        
		                        f.write("Time "+t+". Customer "+ demand[d][0] +
		                        		" picked up by Cab "+ cabs[c][0] + "\n");
		                        // assign the cab to customer's trip
		                        cabs[c][1] = demand[d][1];
		                        cabs[c][2] = demand[d][2];
		                        cabs[c][3] = demand[d][0];
		                        cabs[c][4] = 1;
		                        cabs[c][5] = t;
		                        break;
			                }
			        }
			        else { // a trip has just been completed
		                cabs[c][1] = cabs[c][2]; // frm=to # "I am free!"
		                cabs[c][3] = -1; // unassigned
		                cabs[c][4] = 0; // no passenger on board;
		                cabs[c][5] = -1; // no time started; maybe useful to count wasted time ?
		                f.write("Time "+t+". Cab "+cabs[c][0]+
		                		" is free at stand "+cabs[c][2] +"\n");
			        }
			    }
			 // create demand for the solver
	         int tempDemandCount = createTempDemand(t,f);
		     if (tempDemandCount == 0) continue; // don't solve anything
			 int tempSupplyCount = createTempSupply();
			 f_solv.write("Count of demand="+ tempDemandCount +", supply="+ tempSupplyCount+ " ");
			 int n = 0;
					 
			 // SOLVER
			 if (tempSupplyCount>0) {
				 n = calculate_cost(tempDemandCount, tempSupplyCount);
				 costLCM = Arrays.stream(cost).map(int[]::clone).toArray(int[][]::new);
				 if (n>800) {
					 int n_pairs = LCM(n, THRESHOLD, f_solv);
					 if (n_pairs == 0) {
						 // critical -> a big model but LCM hasn't helped 
					 }
					 analyzePairs(n, n_pairs); // produce input for the solver
					 // what if n==n_pairs -> no input for the solver
				 }
				 Process p = Runtime.getRuntime().exec(SOLVER_CMD);
				 try {
					p.waitFor();
				} catch (InterruptedException e) {
					e.printStackTrace();
					System.exit(0);
				}
			 }
			 readSolversResult(n);	
			 analyzeSolution(n, t, f, f_solv);

			 System.out.println(t);
			 
			 if (t==40) break;
		}
		f.close();
		f_solv.close();
	}
	
	
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
		        for (int j=0; j<5; j++)
		        	demand[count][j]=Integer.parseInt(lineVector[j]);
		        demand[count][5]=-1;
		        demand[count][7]=-1;		
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
	
	private static int createTempDemand(int t, FileWriter f) throws IOException {
         int count = 0;
		 for (int d=0; d<demandCount; d++)
		    if (demand[d][5] == -1 && t >= demand[d][4]) { // not assigned, not dropped (-2) and not eralier than requested
		       if (t-demand[d][4] > DROP_TIME) { // this customer will never be serviced, after 10 minutes of waiting the customer will look for something else
	                demand[d][5] = -2;
	                f.write("Time "+t+". Customer "+demand[d][0]+" dropped\n");
		       }
		       else {
		    	   temp_demand[count][0] = demand[d][0];
		    	   temp_demand[count][1] = demand[d][1];
		    	   temp_demand[count][2] = demand[d][2];
		    	   count++;
		       }
		    }
	     return count;
	}
	
	private static int createTempSupply() throws IOException {
		 int count =0;
		 for (int c1=0; c1<n_cabs; c1++)
		     if (cabs[c1][1] == cabs[c1][2] && cabs[c1][3] == -1)  // any cab with passenger (maybe stupid to absorb the solver) or standing and not assigned
		            // or c[4]==1 without it we are ignoring cabs during a trip
		     {	 
		    	 temp_supply[count][0] = cabs[c1][0];
		    	 temp_supply[count][1] = cabs[c1][1];
		    	 temp_supply[count][2] = cabs[c1][2];
		    	 count++;
		     }
		 return count;
	}
	
	private static void analyzeSolution(int nn, int t, FileWriter f, FileWriter f_solv) throws IOException {
	  int total_cost = 0, total_count=0;
	  for(int s=0; s<nn; s++) 
		for(int c=0; c<nn; c++) 
		  if (x[nn*s+c]==1 && cost[s][c]< big_cost // not a fake assignment (to balance the model)
		                    // that is an unnecessary check as we commented out 'or c[4]==1 above
		        && temp_supply[s][1]==temp_supply[s][2]) { // a cab with no assignment, no need to check [3]
			  total_cost += cost[s][c];
			  total_count++;
		      // first log the assignment in the trip request
		      for (int d=0; d<demandCount; d++)
		          if (demand[d][0] == temp_demand[c][0]) { // 0: ID
                     demand[d][5] = temp_supply[s][0];
                     demand[d][7] = t;
                     break;
		          }
		          // assign the job to the cab
		      for (int c2=0; c2<n_cabs; c2++)
		        if (cabs[c2][0] == temp_supply[s][0]) { // 0: ID
                  if (temp_supply[s][2]==temp_demand[c][1]) { //: # cab is already there
	                  cabs[c2][1] = temp_demand[c][1];
	                  cabs[c2][2] = temp_demand[c][2];
	                  cabs[c2][3] = temp_demand[c][0];
	                  cabs[c2][4] = 1;
	                  cabs[c2][5] = t;
	                  String str = "Time "+ t +". Customer "+ temp_demand[c][0]
	                		  	+ " assigned to and picked up by Cab "+ temp_supply[s][0] +"\n";
	                  f.write(str);
                  }
	              else if (Math.abs(temp_supply[s][2] - temp_demand[c][1]) < DROP_TIME) { 
	            	  // we have a 1D world and indexes of stands indicate the distance
                    // the check above is not needed, we check the distance in 'calculate_cost'
                    // start the trip without a passenger but to pick up a passenger
                    cabs[c2][1] = temp_supply[s][2];
                    cabs[c2][2] = temp_demand[c][1];
                    cabs[c2][3] = temp_demand[c][0];
                    cabs[c2][4] = 0;
                    cabs[c2][5] = t;
                    String str ="Time "+ t +". Customer "+ temp_demand[c][0] 
                    		+" assigned to Cab "+ temp_supply[s][0] +", cab is heading to the customer\n"; 
                    f.write(str);
	              }
	              break;
		         }
		  }
	  f_solv.write(total_cost +" ("+total_count+")\n");
	}
	
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
	            if (dist [temp_supply[c][2]] [temp_demand[d][1]] < DROP_TIME) // take this possibility only if reasonable time to pick-up a customer
	                // otherwise big_cost will stay in this cell
	                cost[c][d] = dist[temp_supply[c][2]][temp_demand[d][1]];
	    FileWriter fr = new FileWriter(new File(SOLVER_COST_FILE));
	    fr.write( n +"\n");
	    for (c=0; c<n; c++) {
	        for (d=0; d<n; d++) fr.write(cost[c][d]+" ");
	        fr.write("\n");
	    }
	    fr.close();
	    return n;
	}
	
	private static int LCM(int n, int threshold, FileWriter f_solv) throws IOException {
		int total_cost = 0, total_count=0, s, d, i;
		for (i=0; i<n; i++) { // we need to repeat the search (cut off rows/columns) 'n' times
			int min_val = big_cost, s_min = -1, d_min = -1; 
			for (s=0; s<n; s++)
				for (d=0; d<n; d++)
					if (costLCM[s][d] < min_val) {
						min_val = costLCM[s][d];
						s_min = s;
						d_min = d;
					}
			if (min_val == big_cost) break; // no more interesting stuff there
			LCMpair[i][0] = s_min;
			LCMpair[i][1] = d_min;
			total_cost += costLCM[s_min][d_min];
			total_count++;
			// removing the column from further search by assigning big cost
			for (s=0; s<n; s++) costLCM[s][d_min] = big_cost; 
			// the same with the row
			for (d=0; d<n; d++) costLCM[s_min][d] = big_cost;
		}
		f_solv.write(total_cost +"("+ total_count +") ");
		return i; // number of allocated pairs
	}
	
	private static void computeDistances() {
		for (int i=0; i<n_stands; i++)
		    for (int j=i; j<n_stands; j++) {
		        dist[j][i] = j-i; // simplification of distance - stop9 is closer to stop7 than to stop1
		        dist[i][j] = dist[j][i]; 
		    }
	}
	
	private static void initSupply() {
		for (int i=0; i<n_cabs; i++) {
			cabs[i][0] = i;
			cabs[i][1] = (int)(n_stands/2);
			cabs[i][2] = (int)(n_stands/2);
			cabs[i][3] = -1;
			cabs[i][4] = 0;
			cabs[i][5] = -1; 
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
}
