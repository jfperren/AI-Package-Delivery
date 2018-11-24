package company;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.config.Parsers;
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
	
	protected ConstraintOptimizationSolver potentialSolver;
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
	protected int marginalCost = 500;
	
	// Discounts
	protected double initialDiscount = 1.0;
	protected double horizon = 0;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		super.setup(topology, distribution, agent);
			
		tasks = new HashSet<Task>();
		vehicles = agent.vehicles();
		
		initialDiscount = agent.readProperty("initialDiscount", Double.class, 1.0);
		horizon = agent.readProperty("horizon", Integer.class, 0);
		
		for (int k = 0; k < agent.vehicles().size(); k++) {
			currentPlans.add(Plan.EMPTY);
		}
		
		// this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config"+ File.separator + "settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
		
		// the setup method cannot last more than timeout_setup milliseconds
		timeoutSetup = ls.get(LogistSettings.TimeoutKey.SETUP);
		timeoutPlan = ls.get(LogistSettings.TimeoutKey.PLAN);
		timeoutBid = ls.get(LogistSettings.TimeoutKey.BID);
		
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
	
	public double discount(Task task) {
		if (horizon == 0) { return 1.0; }
		
		double discount = Math.min(1, initialDiscount + task.id * (1 - initialDiscount) / horizon);
		logMessage("Discount: " + discount);
		return discount;
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
		
		if (solver == null) {
			potentialSolver = new ConstraintOptimizationSolver(vehicles, tasksAfterAdding(task), 0);
		} else {
//			potentialSolver = new ConstraintOptimizationSolver(vehicles, tasksAfterAdding(task), 0);
			potentialSolver = new ConstraintOptimizationSolver(solver, task);
		}
		
		potentialPlans = potentialSolver.solve(timeoutRatio * timeoutBid, p, neighborhoodSize, nReset);
		potentialCost = costOfMoves(potentialPlans);
		
		return Math.max(potentialCost - currentCost, 0);
	}

	@Override
	public Long askPrice(Task task) {
		double marginalCost = Math.max(0, marginalCost(task));
		logMessage("Marginal Cost: " + marginalCost);
		return (long) marginalCost;
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		
		super.auctionResult(previous, winner, bids);
		
		if (winner == id) {
			tasks = tasksAfterAdding(previous);
			logMessage("New tasks are: " + tasks);
			currentPlans = potentialPlans;
			currentCost = potentialCost;
			solver = potentialSolver;
		}
	}

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {  
    	
    	if (tasks.isEmpty()) {
    		
    		List<Plan> plans = new ArrayList<Plan>();
    		
    		for (int i = 0; i < vehicles.size(); i++) {
    			plans.add(Plan.EMPTY);
    		}
    		
    		return plans;
    		
    	} else {
    		
    		logResults(currentPlans, tasks);
        	
        	solver = new ConstraintOptimizationSolver(vehicles, tasks, 0);
    		return solver.solve(timeoutRatio * timeoutPlan, p, neighborhoodSize, nReset);
    	}
    }
}