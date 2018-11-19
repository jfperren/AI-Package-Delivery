package company;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import logist.LogistPlatform;
import logist.LogistSettings;
import logist.agent.Agent;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import solver.ConstraintOptimizationSolver;


public class SmartCompany extends AbstractCompany {
		
	protected double currentCost = 0;
	protected List<Plan> currentPlans = new ArrayList<Plan>();
	
	protected double potentialCost = 0;
	protected List<Plan> potentialPlans = new ArrayList<Plan>();
	
	protected ConstraintOptimizationSolver solver;
	
	
	// Timeout values from settings
	@SuppressWarnings("unused")
	protected double timeoutSetup;
	protected double timeoutPlan;
	protected double timeoutBid;
	
	// Parameters of the solver
	protected double timeoutRatio = 0.9;
	protected double p = 0.05;
	protected int neighborhoodSize = 30;
	protected int nReset = 500;
	protected int horizon = 8;
	protected int marginalCost = 500;


	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		super.setup(topology, distribution, agent);
		
		tasks = new HashSet<Task>();
		vehicles = agent.vehicles();
		
		for (int k = 0; k < agent.vehicles().size(); k++) {
			currentPlans.add(Plan.EMPTY);
		}
		
		// this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = LogistPlatform.getSettings();
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
		
		// the setup method cannot last more than timeout_setup milliseconds
		timeoutSetup = ls.get(LogistSettings.TimeoutKey.SETUP);
		timeoutPlan = ls.get(LogistSettings.TimeoutKey.PLAN);
		timeoutBid = ls.get(LogistSettings.TimeoutKey.BID);
		
	}
	
	public int averageMarginalCost(int k, int n) {
		
		int marginalCost = 0;

		for (int i = 0; i < n; i++) {
		
			Set<Task> tasks = generateTasks(k);
			Task additionalTask = generateTask(k+1);
			
			ConstraintOptimizationSolver oldSolver = new ConstraintOptimizationSolver(vehicles, tasks, 0);
			List<Plan> oldPlans = oldSolver.solve(timeoutRatio * timeoutPlan / (2 * n), p, neighborhoodSize, nReset);
			double oldCost = costOfMoves(oldPlans);		
			
			tasks.add(additionalTask);
			
			ConstraintOptimizationSolver newSolver = new ConstraintOptimizationSolver(vehicles, tasks, 0);
			List<Plan> newPlans = newSolver.solve(timeoutRatio * timeoutPlan / (2 * n), p, neighborhoodSize, nReset);
			double newCost = costOfMoves(newPlans);		
			
			marginalCost += (newCost - oldCost) / n;
			
		}
		
		return marginalCost;
	}
	
	public Task generateTask(int id) {
		
		double t = 0;
		double p = Math.random();
		
		for (City start: topology) {
			
			for (City end: topology) {
				
				t += distribution.probability(start, end);
				
				if (p < t) {
					return new Task(id, start, end, 0, distribution.weight(start, end));
				}
			}
		}
		
		return null;
	}
	
	public Set<Task> generateTasks(int k) {
		
		Set<Task> tasks = new HashSet<Task>();
		
		for (int i = 0; i < k; i++) {
			tasks.add(generateTask(i));
		}
		
		return tasks;
	}
	
	public Set<Task> tasksAfterAdding(Task task) {
		Set<Task> copy = new HashSet<Task>();
		copy.addAll(tasks);
		copy.add(task);
		return copy;
	}
	
	public double marginalCost(Task task) {
		
		solver = new ConstraintOptimizationSolver(vehicles, tasksAfterAdding(task), 0);

		potentialPlans = solver.solve(timeoutRatio * timeoutBid, p, neighborhoodSize, nReset);
		potentialCost = costOfMoves(potentialPlans);
		
		return Math.max(potentialCost - currentCost, 0);
	}

	@Override
	public Long askPrice(Task task) {		
		return (long) Math.max(marginalCost(task), 0);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		
		super.auctionResult(previous, winner, bids);
		
		if (winner == id) {
			tasks = tasksAfterAdding(previous);
			logMessage("New tasks are: " + tasks);
			currentPlans = potentialPlans;
			currentCost = potentialCost;
		}
	}

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {  
    	
    	logResults(currentPlans, tasks);
    	
    	solver = new ConstraintOptimizationSolver(vehicles, tasks, 0);
		return solver.solve(timeoutRatio * timeoutPlan, p, neighborhoodSize, nReset);
    }
}