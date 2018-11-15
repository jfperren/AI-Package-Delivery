package company;

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
import solver.ConstraintOptimizationSolver;


public class SelfCenteredCompany extends AbstractCompany {

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


	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		super.setup(topology, distribution, agent);
		
		tasks = new HashSet<Task>();
		vehicles = agent.vehicles();
		
		// this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
		
		// the setup method cannot last more than timeout_setup milliseconds
//		timeoutSetup = ls.get(LogistSettings.TimeoutKey.SETUP);
//		timeoutPlan = ls.get(LogistSettings.TimeoutKey.PLAN);
		
        timeoutPlan = 10000;
        
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
		
		System.out.println("-------");
		System.out.println("Cost before: " + currentCost);
		System.out.println("Cost with: " + potentialCost);
		System.out.println("Marginal Cost: " + (potentialCost - currentCost));
		
		return potentialCost - currentCost;
	}

	@Override
	public Long askPrice(Task task) {
		return (long) Math.max(marginalCost(task), 0);
	}

	@Override
	public void auctionResult(Task lastTask, int lastWinner, Long[] lastOffers) {
		
		if (lastWinner == agent.id()) {
			tasks = tasksAfterAdding(lastTask);
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