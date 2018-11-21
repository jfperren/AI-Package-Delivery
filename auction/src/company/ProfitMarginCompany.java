package company;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import logist.LogistPlatform;
import logist.LogistSettings;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import solver.ConstraintOptimizationSolver;


public class ProfitMarginCompany extends AbstractCompany {
	
	static class Margin {
		
		public int margin() {
			return 0;
		}
		
		public void update(Task previous, int winner, Long[] bids) {
			// Nothing
		}
		
		public static Margin fromString(String s) {
			return Margin.fixed(Integer.parseInt(s));
		}
		
		public static Margin fixed(final int margin) {
			
			return new Margin() {
				
				@Override
				public int margin() {
					return margin;
				}
			};
		}
	}

	private List<Vehicle> vehicles;
	private Set<Task> tasks;
	
	private double currentCost = 0;
	private List<Plan> currentPlans = new ArrayList<Plan>();
	
	private double potentialCost = 0;
	private List<Plan> potentialPlans = new ArrayList<Plan>();
	
	private ConstraintOptimizationSolver solver;
	
	// Timeout values from settings
	@SuppressWarnings("unused")
	private double timeoutSetup;
	private double timeoutPlan;
	
	// Parameters of the solver
	double timeoutRatio = 0.9;
	double p = 0.05;
	int neighborhoodSize = 30;
	int nReset = 500;
	
	// Profit parameters
	Margin margin;


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
            ls = Parsers.parseSettings("config"+ File.separator + "settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
		
		// the setup method cannot last more than timeout_setup milliseconds
		timeoutSetup = ls.get(LogistSettings.TimeoutKey.SETUP);
		timeoutPlan = ls.get(LogistSettings.TimeoutKey.PLAN);
		
		margin = Margin.fromString(agent.readProperty("margin", String.class, "0"));
	}
	
	public Set<Task> tasksAfterAdding(Task task) {
		Set<Task> copy = new HashSet<Task>();
		copy.addAll(tasks);
		copy.add(task);
		return copy;
	}
	
	public double marginalCost(Task task) {
		
		solver = new ConstraintOptimizationSolver(vehicles, tasksAfterAdding(task), 0);
		
		potentialPlans = solver.solve(timeoutRatio * timeoutPlan, p, neighborhoodSize, nReset);
		potentialCost = costOfMoves(potentialPlans);
		
		System.out.println("-- Self-Centered #" + agent.id() + "--");
		System.out.println("Cost before: " + currentCost);
		System.out.println("Cost with: " + potentialCost);
		System.out.println("Marginal Cost: " + (potentialCost - currentCost));
		
		return potentialCost - currentCost;
	}

	@Override
	public Long askPrice(Task task) {
		return (long) Math.max(marginalCost(task), 0) + margin.margin();
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		
		super.auctionResult(previous, winner, bids);
		
		if (winner == agent.id()) {
			tasks = tasksAfterAdding(previous);
			currentPlans = potentialPlans;
			currentCost = potentialCost;
		}
	}

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {  
    	
    	logResults(currentPlans, tasks);
    	
    	solver = new ConstraintOptimizationSolver(vehicles, tasks, 0);
		return solver.solve(timeoutRatio * timeoutPlan, p, neighborhoodSize, nReset);
//		potentialCost = costOfMoves(potentialPlans);
    	
//        return currentPlans;
    }
}