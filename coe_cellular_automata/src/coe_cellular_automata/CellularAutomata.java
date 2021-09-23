package coe_cellular_automata;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.stream.DoubleStream;

public class CellularAutomata {
	ArrayList<Cell> cellList = new ArrayList<Cell>();
	ArrayList<Cell> cellListMaximum = new ArrayList<Cell>();
	int numIter=15000;
	double lateSeralTarget,harvestMax,harvestMin, objValue, maxObjValue;
	double globalWeight =0.0;
	boolean finalPlan = false;
	boolean globalConstraintsAchieved = false;
	
	Grid landscape = new Grid();//Instantiate the GRID
	LandCoverConstraint beo = new LandCoverConstraint();
	double[] planHarvestVolume = new double[landscape.numTimePeriods];
	double[] planLateSeral = new double[landscape.numTimePeriods];
	double[] maxPlanHarvestVolume = new double[landscape.numTimePeriods];
	double[] maxPlanLateSeral = new double[landscape.numTimePeriods];
	ArrayList<ArrayList<LinkedHashMap<String, Double>>> yields = new ArrayList<ArrayList<LinkedHashMap<String, Double>>>();
	
	
	//Variables for simulate2
	double maxCutLocal = 0.0;
	double[] maxCutGlobal = new double[landscape.numTimePeriods];
	
	
	/** 
	* Class constructor.
	*/
	public CellularAutomata() {
	}
	
	/** 
	* Simulates the cellular automata. This is the main algorithm for searching the decision space following Heinonen and Pukkala 
	* 1. Local level decisions are optimized by
	* 	a. create a vector of randomly sampled without replacement cell indexes 
	* 	b. pull a random variable and determine if the cell is to be mutated. If mutated pick random state
	* 	c. pull a random variable and determine if the cell is to be innovated. If innovated pick best state
	* 	e. go to next cell
	* 	f. stop when number of iterations reached.
	* 2. Global level decisions are optimized
	* 	a. using the local level as the starting point. Global weight (b) =0;
	* 	b. estimate local + global objective function
	* 	c. for each cell evaluate the best state
	* 	d. increment the global weight b ++ 0.1 and go to the next iteration
	* 	e. stop when global penalties are met
	*/
	public void simulate2() {
		boolean mutate = false;
		boolean innovate = true;
		int counterLocalMaxState = 0;
		
		int[] rand = new Random().ints(0, cellList.size()).distinct().limit(cellList.size()).toArray();; // Randomize the stand or cell list
		Random r = new Random(15); // needed for mutation or innovation probabilities? 
		Arrays.fill(planHarvestVolume, 0.0); //set the harvestVolume indicator
		Arrays.fill(planLateSeral, 0.0); //set the late-seral forest indicator
		
		setCutPriorities();//scope all of the cells to parameterize priority functions
		
		System.out.println("Starting local optimization..");
		//local optimization
		for(int i = 0; i < 50; i ++) {
			//Local level optimization
			System.out.println("Local optimization iter:" + i);
			
			for(int j = 0; j < rand.length; j++) {
				if(mutate) {
					cellList.get(rand[j]).state = r.nextInt(cellList.get(rand[j]).statesHarvest.size()); //get a random state
				}
				if(innovate) {
					if(cellList.get(rand[j]).state == getMaxStateLocal(rand[j])) {
						counterLocalMaxState ++;
						continue;
					}else {
						cellList.get(rand[j]).state = getMaxStateLocal(rand[j]); //set the maximum state with no global constraints
					}
				}
			}
			
			if(counterLocalMaxState == rand.length) {
				break;
			}else {
				counterLocalMaxState = 0;
			}
			
		}
		
		//Set the global level objectives
		for(int c =0; c < cellList.size(); c++) {// Iterate through each of the cell and their corresponding state
			int state = cellList.get(c).state;
			planHarvestVolume = sumVector(planHarvestVolume, cellList.get(c).statesHarvest.get(state)) ;//harvest volume
			//planLateSeral = sumVector(planLateSeral, cellList.get(c).statesOG.get(state));
			
			for(int lc = 0; lc < cellList.get(c).landCoverList.size(); lc ++) { //Land cover constraints
				double [] tempLCValue = new double[landscape.numTimePeriods];
				tempLCValue = beo.landCoverConstraintList.get(cellList.get(c).landCoverList.get(lc)).get("Actual");
				tempLCValue = sumVector(tempLCValue, cellList.get(c).statesOG.get(cellList.get(c).state));
				beo.landCoverConstraintList.get(cellList.get(c).landCoverList.get(lc)).put("Actual", tempLCValue);
			}
		}
		
		System.out.println("Starting global optimization..");
		//global optimization
		for(int g =0; g < 10000; g++) {
			
			if(globalConstraintsAchieved) {
				break;
			}
			
			for(int j = 0; j < rand.length; j++) {				
				cellList.get(rand[j]).state = getMaxStateGlobal(rand[j]); //set the maximum state with global constraints	
				
				//Add local contribution to global.
				planHarvestVolume = sumVector(planHarvestVolume, cellList.get(rand[j]).statesHarvest.get(cellList.get(rand[j]).state));
				for(int lc =0 ; lc < cellList.get(rand[j]).landCoverList.size(); lc ++) {
					beo.landCoverConstraintList.get(cellList.get(rand[j]).landCoverList.get(lc)).put("Actual", sumVector(beo.landCoverConstraintList.get(cellList.get(rand[j]).landCoverList.get(lc)).get("Actual"), cellList.get(rand[j]).statesOG.get(cellList.get(rand[j]).state)));
				}
				//planLateSeral = sumVector(planLateSeral, cellList.get(rand[j]).statesOG.get(cellList.get(rand[j]).state));
			}
			
			System.out.print("iter:" + g + " global weight:" + globalWeight );
			for(int p =0; p< planHarvestVolume.length; p++) {
				System.out.print(" vol @ " + p + "= " + planHarvestVolume[p]);
				System.out.print(" zero ls @ " + p + " = " + beo.landCoverConstraintList.get(0).get("Actual")[p]);
				System.out.print(" one ls @ " + p + " = " + beo.landCoverConstraintList.get(1).get("Actual")[p]);
				System.out.print(" two ls @ " + p + " = " + beo.landCoverConstraintList.get(2).get("Actual")[p]);
			}
			System.out.println();
			
			globalWeight = globalWeight + 0.01; //increment the global weight	
		}
		
		//Final plan
		System.out.println();
		System.out.println("Preserved" );
		int rowCounter = 0;
		/*for (int l= 0; l < cellList.size(); l++){
			if (cellList.get(l).state == 0) {
				System.out.print(".");
	        }else {
	        	System.out.print("*");
	        }
	        rowCounter ++;
	        if(rowCounter == landscape.colSizeLattice) {
	        	System.out.println();
	            rowCounter = 0;
	        }
	     } */      		
	}
	
	/**
	* Finds the quantity across all cells and schedules of the maximum amount of volume harvested 
	* across all planning horizons (maxCutLocal).
	*/
	private void setCutPriorities() {
		double tempCut = 0.0;	
		for(int c =0; c < cellList.size(); c++) {
			for(int s= 0; s < cellList.get(c).statesHarvest.size(); s++ ) {				
				tempCut = tempCut + DoubleStream.of(cellList.get(c).statesHarvest.get(s)).sum();	
				if(maxCutLocal < tempCut) {
					maxCutLocal = tempCut;
				}		
				tempCut = 0.0;
			}		
		}
		//Note that the max og local is 1 for all periods. 
	}
	
	/**
	* Retrieves the max state at the local scale. The rank of alternative schedules is based on Heinonen and Pukkala:
	* Ujk = SUM(wi*ui(qi)) where wi is the weight for objective i, 
	* ui is the priority function for objective i and qi is the quantity of the objective i 
	* 
	* @param i the cell index
	* @return the index of the cell state which maximizes the objectives
	*/
	private int getMaxStateLocal(int i) {
		double maxValue = 0.0, stateValue = 0.0;
		double isf =0.0, dsf = 0.0;
		int stateMax = 0;

		for(int s = 0; s < cellList.get(i).statesHarvest.size(); s++) {
			
			isf = DoubleStream.of(cellList.get(i).statesHarvest.get(s)).sum()/maxCutLocal;		
			dsf = DoubleStream.of(sumVector(cellList.get(i).statesOG.get(s), getNeighborLateSeral(cellList.get(i).adjCellsList))).sum()/(landscape.numTimePeriods*2);
			
			stateValue = 0.3*isf + 0.7*dsf;
			
			if(maxValue < stateValue) {
				maxValue = stateValue;
				stateMax = s;
			}
		}
		
		return stateMax;
	}

	/**
	* Retrieves the max state when linking the local and global objectives. 
	* The rank of alternative schedules is based on Heinonen and Pukkala:
	*
	* Local level rank of alternative states
	* Ujk = SUM(wi*ui(qi)) where wi is the weight for objective i, 
	* ui is the priority function for objective i and qi is the quantity of the objective i 
	*
	* Global level rank of alternative states
	* P =SUM(vl*pl(gl)) where vl is the weight for global objective l, 
	* pl is the priority function for objective l and gl is the quantity of the objective l 
	* 
	* Combination rank or linkage between the two scales
	* Rjk = a/A*Ujk + b*P where Rjk is the rank of alternative states, a is the
	* area of the cell, A is the total area of all cells, b is the globalWeight to be incremented.
	* 
	* @param id the cell index
	* @return the index of the cell state which maximizes the objectives
	*/
	private int getMaxStateGlobal(int id) {
		double maxValue = 0.0, P = 0.0, U =0.0 ,propLC =0.0, remainingLC = 0.0;
		double isf =0.0, dsf = 0.0;
		double stateValue;
		int stateMax = 0;

		//remove the contribution of this cell to the global objective
		planHarvestVolume = subtractVector(planHarvestVolume, cellList.get(id).statesHarvest.get(cellList.get(id).state));
		//planLateSeral = subtractVector(planLateSeral, cellList.get(id).statesOG.get(cellList.get(id).state));
		
		for(int lc = 0 ; lc < cellList.get(id).landCoverList.size(); lc ++) { //remove the cells current contribution (given its current state) to each land cover constraint
			beo.landCoverConstraintList.get(cellList.get(id).landCoverList.get(lc)).put("Actual", subtractVector(beo.landCoverConstraintList.get(cellList.get(id).landCoverList.get(lc)).get("Actual"), cellList.get(id).statesOG.get(cellList.get(id).state)));
		}
		
		for(int rlc = 0; rlc < beo.landCoverConstraintList.size(); rlc ++) { //get the remaining landcover constraints the cell doesn't belong to
			if(cellList.get(id).landCoverList.contains(rlc)) {
				continue;
			}else {
				remainingLC = remainingLC + DoubleStream.of(multiplyScalar(checkMaxContribution(divideVector(beo.landCoverConstraintList.get(rlc).get("Actual"), beo.landCoverConstraintList.get(rlc).get("Target"))),  1.0/landscape.numTimePeriods)).sum();
				
			}
		}
		
		for(int s = 0; s < cellList.get(id).statesPrHV.size(); s++) { // Iterate through each of the plausible treatment schedules also known as states
		
			isf = DoubleStream.of(cellList.get(id).statesHarvest.get(s)).sum()/maxCutLocal; 
			dsf = DoubleStream.of(sumVector(cellList.get(id).statesOG.get(s), getNeighborLateSeral(cellList.get(id).adjCellsList))).sum()/(landscape.numTimePeriods*2);
			
			U = 0.3*isf + 0.7*dsf;
			
			propLC = 0.0;
			for(int lc =0; lc < cellList.get(id).landCoverList.size(); lc++) { // land cover constraints the cell belongs to
				propLC = propLC + DoubleStream.of(multiplyScalar(checkMaxContribution(divideVector(sumVector(beo.landCoverConstraintList.get(cellList.get(id).landCoverList.get(lc)).get("Actual"), cellList.get(id).statesOG.get(s)), beo.landCoverConstraintList.get(cellList.get(id).landCoverList.get(lc)).get("Target"))),  1.0/landscape.numTimePeriods)).sum();
			}
						
			P =  0.3*DoubleStream.of(multiplyScalar(checkMaxContribution(divideScalar(sumVector(planHarvestVolume, cellList.get(id).statesHarvest.get(s)), harvestMin)), 1.0/landscape.numTimePeriods)).sum() +
					0.7*((propLC + remainingLC)/beo.landCoverConstraintList.size());

			stateValue = landscape.weight*U + globalWeight*P;
			
			if(maxValue < stateValue) {
				maxValue = stateValue;
				stateMax = s;
				if(P > 0.999) { //this is the threshold for stopping the simulation
					globalConstraintsAchieved = true;
				}
			}
			
		};
		
		
		return stateMax;
	}

	/**
	 * Resets any objectives whose value is greater than one to a max of one
	 * @param objective an array of objective values
	 * @return an array whose elements have a max value of 1.0
	 */
	private double[] checkMaxContribution(double[] objective) {
		double out[] = new double[landscape.numTimePeriods];
		out = objective.clone();
		
		for(int t = 0; t < objective.length; t++) {
			if(objective[t] > 1.0) {
				out[t] = 1.0;
			}
		}
		return out;
	}

	/** 
	* Simulates the cellular automata follow Mathey et al. This is the main algorithm for searching the decision space. 
	* 1. global level penalties are determined which incentivize cell level decisions
	* 2. create a vector of randomly sampled without replacement cell indexes 
	* 3. the first random cell is tested if its at maximum state which includes context independent values such as
	* the maximum amount of volume the cell can produce of the planning horizon and context dependent values such as
	* its contribution, as well as, the surrounding cells contribution to late-seral forest targets. 
	* 4. If already at max state then proceed to the next cell. Else update to its max state.
	* 5. If there are no more stands to change or the number of iterations has been reached - end.
	*/
	public void simulate() {
		int block = 0;
		int numIterSinceFreq = 0;
		int[] blockParams = {0, 2000, 4000,7000,10000, 1000000}; // add a large number so there's no out of bounds issues
		int[] freqParams = {0, 300,200,100,1,1};
		boolean timeToSetPenalties = false;
		int [] maxStates = new int[cellList.size()];
		landscape.setPenaltiesBlank();//set penalty parameters - alpha, beta and gamma as zero filled arrays
		
		for(int i=0; i < numIter; i++) { // Iteration loop
					
			if(i >= blockParams[block+1]) { // Go to the next block
				block ++;
			}
			
			if(block > 0 && numIterSinceFreq >= freqParams[block]) { // Calculate at the freq level
				timeToSetPenalties = true;
				numIterSinceFreq = 0;
			}
				
			numIterSinceFreq ++;
			
			Arrays.fill(planHarvestVolume, 0.0); //set the harvestVolume indicator
			Arrays.fill(planLateSeral, 0.0); //set the late-seral forest indicator
			objValue = 0.0; //reset the object value;
			
			int[] rand = new Random().ints(0, cellList.size()).distinct().limit(cellList.size()).toArray();; // Randomize the stand or cell list
			
			for(int j = 0; j < rand.length; j++) { //Stand or cell list loop
				int maxState = getMaxState(rand[j]);
				if(cellList.get(rand[j]).state == maxState) { //When the cell is at its max state - go to the next cell
					//System.out.println("Cell:" + cellList.get(rand[j]).id + " already at max");
					if(j == cellList.size()-1) {
						finalPlan = true;
					}
					continue; // Go to the next cell -- this one is already at its max
				}else{ // Change the state of the cell to its max state and then exit the stand or cell list loop
					System.out.println("Cell:" + cellList.get(rand[j]).id + " change from " + cellList.get(rand[j]).state + " to " + maxState );
					cellList.get(rand[j]).state = maxState; //transition function - set the new state to the max state
					break;
				}
				
			}
			
			//Output the global indicators (aggregate all cell level values)
			for(int c =0; c < cellList.size(); c++) {// Iterate through each of the cell and their corresponding state
				int state = cellList.get(c).state;
				double isf, dsf;
					
				//isf = DoubleStream.of(multiplyVector(cellList.get(c).statesPrHV.get(state), sumVector(landscape.lambda, subtractVector(landscape.alpha,landscape.beta)))).sum(); //I s(f) is the context independent component of the obj function			
				//dsf = DoubleStream.of(multiplyVector(divideScalar(sumVector(cellList.get(c).statesOG.get(state), getNeighborLateSeral(cellList.get(c).adjCellsList)), landscape.numTimePeriods*2), sumVector(landscape.lambda,landscape.gamma))).sum();
				isf = DoubleStream.of(multiplyVector(landscape.lambda,multiplyVector(cellList.get(c).statesPrHV.get(state), subtractVector(landscape.alpha,landscape.beta)))).sum(); //I s(f) is the context independent component of the obj function			
				dsf = DoubleStream.of(multiplyVector(landscape.oneMinusLambda, multiplyVector(divideScalar(sumVector(cellList.get(c).statesOG.get(state), getNeighborLateSeral(cellList.get(c).adjCellsList)), landscape.numTimePeriods*2), landscape.gamma))).sum();
					
				objValue += isf + dsf; //objective value
				
				planHarvestVolume = sumVector(planHarvestVolume, cellList.get(c).statesHarvest.get(state)) ;//harvest volume
				planLateSeral = sumVector(planLateSeral, cellList.get(c).statesOG.get(state));
			}
			System.out.println("iter:"+ i + " obj:" + objValue);
			if(maxObjValue < objValue && i > 14000) {
				maxObjValue = objValue;
				maxPlanHarvestVolume = planHarvestVolume.clone();
				maxPlanLateSeral = planLateSeral.clone();
				
				for(int h =0; h < cellList.size(); h++) {
					maxStates[h] = cellList.get(h).state;
				}
			}
			//Set the global-level penalties
			if(timeToSetPenalties) {
				double[] alpha = getAlphaPenalty(planHarvestVolume, harvestMin);
				double[] beta = getBetaPenalty(planHarvestVolume, harvestMax);
				double[] gamma = getGammaPenalty(planLateSeral, lateSeralTarget);
				landscape.setPenalties(alpha, beta, gamma);
				timeToSetPenalties = false;
			}
			
			if(finalPlan || i == numIter-1) {
				System.out.println("All cells at max state in iteration:" + i);
				System.out.println("maxObjValue:" + maxObjValue);
				for(int t= 0; t < planHarvestVolume.length; t++) {
					System.out.print("HV @ " + t + ": " + planHarvestVolume[t]+", ");
					System.out.println();
					System.out.print("HV @ " + t + ": " + maxPlanHarvestVolume[t]+", ");
					System.out.println();
					System.out.print("LS @ " + t + ": " + planLateSeral[t]+", ");
					System.out.println();
					System.out.print("LS @ " + t + ": " + maxPlanLateSeral[t]+", ");
					System.out.println();
				}
				System.out.println();
				//print the grid at each time
				/*for(int g= 0; g < landscape.numTimePeriods; g++) {
				   System.out.println("Time Period:" + (g + 1));
				   int rowCounter = 0;
			        for (int l= 0; l < cellList.size(); l++){
			            if (cellList.get(l).statesOG.get(cellList.get(l).state)[g] == 0.0) {
			            	System.out.print(".");
			            }else {
			            	System.out.print("*");
			            }
			            rowCounter ++;
			            if(rowCounter == landscape.colSizeLattice) {
			            	 System.out.println();
			            	 rowCounter = 0;
			            }
			         }
			        
			        System.out.println();
			        System.out.println();
			        System.out.println(maxObjValue);
				}*/
				break;
			}
		}
		
		//Report final plan indicators
	}
	
	/**
	* Retrieves the penalty for late-seral forest
	* @param planLateSeral2	the plan harvest volume
	* @param lateSeralTarget2	the minimum amount of late-seral needed
	* @return 		an array of gamma penalties
	*/
	 private double[] getGammaPenalty(double[] planLateSeral2, double lateSeralTarget2) {
			double[] gamma = new double[landscape.numTimePeriods];
			for(int a = 0; a < planLateSeral2.length; a++ ) {
				if(planLateSeral2[a] <= lateSeralTarget2) {
					if(planLateSeral2[a] == 0.0) {//check divisible by zero
						gamma[a] = lateSeralTarget2/0.00001; //use a small number in lieu of zero
					}else {
						gamma[a] = lateSeralTarget2/planLateSeral2[a];
					}
				}else {
					gamma[a] = 0.0;
				}
				
			}
			return gamma;
	}
	 
	/**
	* Retrieves the penalty for over harvesting
	* @param planHarvestVolume2	the plan harvest volume
	* @param harvestMax2	the maximum harvest volume
	* @return 		an array of beta penalties
	*/
	private double[] getBetaPenalty(double[] planHarvestVolume2, double harvestMax2) {
			double[] beta = new double[landscape.numTimePeriods];
			for(int a = 0; a < planHarvestVolume2.length; a++ ) {
				if(planHarvestVolume2[a] >= harvestMax2) {
					beta[a] = planHarvestVolume2[a]/harvestMax2;
				}else {
					beta[a] = 0.0;
				}				
			}
			return beta;
	}
	
	/**
     * Retrieves the penalty for under harvesting
     * @param planHarvestVolume2	the plan harvest volume
     * @param harvestMin2	the minimum harvest volume
     * @return 		an array of alpha penalties
     */
	private double[] getAlphaPenalty(double[] planHarvestVolume2, double harvestMin2) {
		double[] alpha = new double[landscape.numTimePeriods];
		for(int a = 0; a < planHarvestVolume2.length; a++ ) {
			if(planHarvestVolume2[a] <= harvestMin2) {
				if(planHarvestVolume2[a] == 0.0) {//check divisible by zero
					alpha[a] = harvestMin2/0.001; //use a small number in lieu of zero
				}else {
					alpha[a] = harvestMin2/planHarvestVolume2[a];
				}
			}else {
				alpha[a] = 0.0;
			}			
		}
		return alpha;
	}

	/**
     * Retrieves the schedule or state with the maximum value for this cell object
     * @param id	the index of the cell or stand
     * @return 		an integer representing the maximum state of a cell
     */
	public int getMaxState(int id) {
		double maxValue = 0.0;
		double stateValue, isf,dsf;
		int stateMax =0;
		double[] lsn = new double[landscape.numTimePeriods];
		lsn = getNeighborLateSeral(cellList.get(id).adjCellsList);
	
		for(int i = 0; i < cellList.get(id).statesPrHV.size(); i++) { // Iterate through each of the plausible treatment schedules also known as states
		
			//isf = DoubleStream.of(multiplyVector(cellList.get(id).statesPrHV.get(i), sumVector(landscape.lambda, subtractVector(landscape.alpha,landscape.beta)))).sum(); //I s(f) is the context independent component of the obj function			
			//dsf = DoubleStream.of(multiplyVector(divideScalar(sumVector(cellList.get(id).statesOG.get(i), lsn), landscape.numTimePeriods*2), sumVector(landscape.oneMinusLambda,landscape.gamma))).sum();
			isf = DoubleStream.of(multiplyVector(landscape.lambda,multiplyVector(cellList.get(id).statesPrHV.get(i), subtractVector(landscape.alpha,landscape.beta)))).sum(); //I s(f) is the context independent component of the obj function			
			dsf = DoubleStream.of(multiplyVector(landscape.oneMinusLambda, multiplyVector(divideScalar(sumVector(cellList.get(id).statesOG.get(i), lsn), landscape.numTimePeriods*2.0), landscape.gamma))).sum();
			
			stateValue = isf + dsf;
			if(maxValue < stateValue) {
				maxValue = stateValue;
				stateMax = i;
			}
		};
		
		return stateMax;
	}
		
	 /**
     * Retrieves a factor between 0 and 1 that is equal to the proportion of stand f's neighbors 
     * that are also late-seral in planning period t
     * @param adjCellsList	an ArrayList of integers representing the cells index + 1
     * @return 		a vector of length equal to the number of time periods
     */
	public double[] getNeighborLateSeral(ArrayList<Integer> adjCellsList) {
		double[] lsn = new double[landscape.numTimePeriods];
		double lsnTimePeriod = 0.0;
		int counter = 0;
		
		for(int t =0; t < landscape.numTimePeriods; t++) {
			for(int n =0; n < adjCellsList.size(); n++) {
				int state = cellList.get(adjCellsList.get(n)-1).state; // the cellList is no longer in order can't use get. Need a comparator.
				lsnTimePeriod += cellList.get(adjCellsList.get(n)-1).statesOG.get(state)[t];
				counter ++;
			}
			lsn[t] = lsnTimePeriod/counter;
			counter = 0;
			lsnTimePeriod = 0.0;
		}
		
		return lsn;
	}

	 /**
     * Multiplies two vectors together to return the element wise product.
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param vector2	an Array of doubles with length equal to the number of time periods
     * @return 		a vector of length equal to the number of time periods
     * @see divideVector
     */
	private double[] multiplyVector (double[] vector1, double[] vector2) {
		double[] outVector = new double[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = vector1[i]*vector2[i];
		}
		return outVector;
	}
	
	 /**
     * Divides two vectors together to return the element wise product.
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param vector2	an Array of doubles with length equal to the number of time periods
     * @return 		a vector of length equal to the number of time periods
     * @see multiplyVector
     */
	private double[] divideVector (double[] vector1, double[] vector2) {
		double[] outVector = new double[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = vector1[i]/vector2[i];
		}
		return outVector;
	}
	
	 /**
     * Divides a vectors by a scalar element wise.
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param scalar	a scalar
     * @return 		a vector of length equal to the number of time periods
     * @see multiplyScalar
     */
	private double[] divideScalar (double[] vector1, double scalar) {
		double[] outVector = new double[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = vector1[i]/scalar;
		}
		return outVector;
	}
	
	 /**
     * Multiplies a vectors by a scalar element wise.
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param scalar	a scalar
     * @return 		a vector of length equal to the number of time periods
     * @see divideScalar
     */
	private double[] multiplyScalar (double[] vector1, double scalar) {
		double[] outVector = new double[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = vector1[i]*scalar;
		}
		return outVector;
	}
	
	 /**
     * Subtracts two vectors so that the element wise difference is returned. The first vector is subtracted by the second
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param vector2	an Array of doubles with length equal to the number of time periods
     * @return 		a vector of length equal to the number of time periods
     * @see sumVector
     */
	private double[] subtractVector (double[] vector1, double[] vector2) {
		double[] outVector = new double[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = vector1[i]-vector2[i];
		}
		return outVector;
	}
	
	 /**
     * Adds two vectors so that the element wise sum is returned.
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param scalar	a scalar
     * @return 		a vector of length equal to the number of time periods
     * @see subtractVector
     */
	private double[] sumVector(double[] vector1, double[] vector2) {
		double[] outVector = new double[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = vector1[i]+vector2[i];
		}
		return outVector;
	}
	
	 /**
     * Instantiates java objects developed in R
     */
	public void setRParms() {
		// TODO Auto-generated method stub
	}
	
	 /**
     * Creates a forest data set used for testing the cellular automata
     */
	public void createData() {
		//harvest flow
		lateSeralTarget = Math.round(0.2*landscape.numCells); // 15% of the landscape should be old growth
		harvestMax = 32500.0;
		harvestMin = 32000.0;
		
		Random r =	new Random(); //Random seed for making new grids
		double scale = 3.7;
		double shape = 2.9;
		
		
		//dummy yields taken from yieldid -203322
		Double vols[] = {0.0,0.0,0.0,0.0,10.22,45.4,95.32,148.35,198.33,243.29,283.14,318.13,349.0,377.2,402.6,422.64,435.88,443.75,447.82,449.16,448.54,444.25,439.52,434.92,430.47,426.17,422.0,417.96,414.02,410.19,406.46,402.82,400.28,398.41,396.56,394.73};
		Double hts[] = {0.0, 0.6,2.6,6.41,10.62,14.6,18.15,21.22,23.85,26.1,28.01,29.66,31.07,32.29,33.36,34.29,35.1,35.83,36.47,37.04,37.55,38.01,38.43,38.81,39.15,39.46,39.75,40.01,40.25,40.48,40.68,40.87,41.05,41.22,41.37,41.51};
		Double ogs[] = {0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0};
		//YieldID = 0
		yields.add(0, new ArrayList <LinkedHashMap<String, Double >>());
		
		Double vols2[] = {0.0,0.0,10.22,45.4,95.32,148.35,198.33,243.29,283.14,318.13,349.0,377.2,402.6,422.64,435.88,443.75,447.82,449.16,448.54,444.25,439.52,434.92,430.47,426.17,422.0,417.96,414.02,410.19,406.46,402.82,400.28,398.41,396.56,394.73,394.73,394.73};
		Double hts2[] = {0.0, 0.6,2.6,6.41,10.62,14.6,18.15,21.22,23.85,26.1,28.01,29.66,31.07,32.29,33.36,34.29,35.1,35.83,36.47,37.04,37.55,38.01,38.43,38.81,39.15,39.46,39.75,40.01,40.25,40.48,40.68,40.87,41.05,41.22,41.37,41.51};
		Double ogs2[] = {0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0};
		//YieldID = 1
		yields.add(1, new ArrayList <LinkedHashMap<String, Double >>());
		
		//Add in yields using a loop
		for(int y=0; y<36; y++) { // for each decade in the yield curve to a max of 350 years including year 0
			yields.get(0).add(y, new LinkedHashMap<String, Double >() );
			yields.get(0).get(y).put("vol", vols[y]);
			yields.get(0).get(y).put("ht", hts[y]);
			yields.get(0).get(y).put("og", ogs[y]);
			yields.get(1).add(y, new LinkedHashMap<String, Double >() );
			yields.get(1).get(y).put("vol", vols2[y]);
			yields.get(1).get(y).put("ht", hts2[y]);
			yields.get(1).get(y).put("og", ogs2[y]);
		}
		//Create a landcover constraint
		double[] temp = new double[landscape.numTimePeriods];
		double[] temp1 = new double[landscape.numTimePeriods];
		double[] temp2 = new double[landscape.numTimePeriods];
		double[] temp3 = new double[landscape.numTimePeriods];
		double[] temp4 = new double[landscape.numTimePeriods];
		
		Arrays.fill(temp, 0.0);
		beo.landCoverConstraintList.add(0, new LinkedHashMap<String, double[] >());
		beo.landCoverCellList.add(0, new ArrayList<Integer>());
		beo.landCoverConstraintList.get(0).put("Actual", temp);

		beo.landCoverConstraintList.add(1, new LinkedHashMap<String, double[]>());	
		beo.landCoverCellList.add(1, new ArrayList<Integer>());
		beo.landCoverConstraintList.get(1).put("Actual", temp.clone());
		
		beo.landCoverConstraintList.add(2, new LinkedHashMap<String, double[]>());	
		beo.landCoverCellList.add(2, new ArrayList<Integer>());
		beo.landCoverConstraintList.get(2).put("Actual", temp.clone());
		
		beo.landCoverConstraintList.add(3, new LinkedHashMap<String, double[]>());	
		beo.landCoverCellList.add(3, new ArrayList<Integer>());
		beo.landCoverConstraintList.get(3).put("Actual", temp.clone());
		
		Arrays.fill(temp1, 250.0);
		beo.landCoverConstraintList.get(0).put("Target", temp1);
		Arrays.fill(temp2, 250.0);
		beo.landCoverConstraintList.get(1).put("Target", temp2);
		Arrays.fill(temp3, 10.0);
		beo.landCoverConstraintList.get(2).put("Target", temp3);
		Arrays.fill(temp4, 30.0);
		beo.landCoverConstraintList.get(3).put("Target", temp4);
		
		ArrayList<Integer> lc = new ArrayList<>(Arrays.asList(0,2));
		ArrayList<Integer> lc1 = new ArrayList<>(Arrays.asList(1));
		ArrayList<Integer> lc2 = new ArrayList<>(Arrays.asList(0));
		ArrayList<Integer> lc3 = new ArrayList<>(Arrays.asList(1,3));
		
	
		for(int k= 0; k < landscape.numCells; k++) {
			//Attribution of the stand or cell
			int age = (int) Math.round(Math.exp(scale + shape * Math.abs(r.nextGaussian())/4)/10)*10;
			if(age > 250) { 
				age = 250 ;
			};
			System.out.println(age);
			//int age = (int) (Math.round(distribution.sample()*10)/10); //random age in 10 year classes
			//int age = 0;
			if(k < 1250) {
				if(k<80) {
					this.cellList.add(new Cell(landscape, k + 1, age, yields.get(0), yields.get(1), lc ));
					beo.landCoverCellList.get(0).add(k);
					beo.landCoverCellList.get(2).add(k);
				}else {
					this.cellList.add(new Cell(landscape, k + 1, age, yields.get(0), yields.get(1), lc2));
					beo.landCoverCellList.get(0).add(k);
				}
			}else {
				if(k > 2300) {
					this.cellList.add(new Cell(landscape, k + 1, age, yields.get(0), yields.get(1), lc3));
					beo.landCoverCellList.get(1).add(k);
					beo.landCoverCellList.get(3).add(k);
				}else {
					this.cellList.add(new Cell(landscape, k + 1, age, yields.get(0), yields.get(1), lc1));
				beo.landCoverCellList.get(1).add(k);
				}
				
			}
		}				
		System.out.println("create data done");
	}

}