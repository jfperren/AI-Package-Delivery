package company;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.plan.Action;
import logist.plan.ActionHandler;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;


abstract class AbstractCompany implements AuctionBehavior {
	
	// Knowledge of the problem
	protected Topology topology;
	protected TaskDistribution distribution;

	// Knowledge of our means
	protected List<Vehicle> vehicles = new ArrayList<Vehicle>();
	protected Set<Task> tasks = new HashSet<Task>();
	protected int id;
	
	// Other stuff
	protected Random random;
	protected boolean log = false;	
	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		
		this.vehicles.addAll(agent.vehicles());
		this.id = agent.id();
		
		long seed = -9019554669489983951L * agent.id();
		this.random = new Random(seed);
	}
	
    protected double costOfMoves(List<Plan> plans) {
    	
    	double reward = 0;
        
        for (int k = 0; k < vehicles.size(); k++) {
        	reward += plans.get(k).totalDistance() * vehicles.get(k).costPerKm();
        }
        
        return reward;
    }
    
    protected Map<Task, Double> costOfTasks(List<Plan> plans) {
    	
    	Map<Task, Double> costPerTask = new HashMap<Task, Double>();
    	
    	for (int i = 0; i < plans.size(); i++) {
    		
    		City initialCity = vehicles.get(i).homeCity();
    		Plan plan = plans.get(i);
    		int costPerKm = vehicles.get(i).costPerKm();
    		
    		Map<Task, Double> costsInPlan = new CostCounter().costOfTasks(initialCity, plan, costPerKm);
    		
    		costPerTask.putAll(costsInPlan);    		
    	}
    	
    	return costPerTask;
    }
    
    protected double rewardOfTasks(TaskSet tasks) {
    	
    	double reward = 0;
    	
    	for (Task task: tasks) {
    		reward += task.reward;
    	}
    	
    	return reward;
    }
    
    @Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
    
    	if (id == winner && log) {
    		System.out.println("---");
        	System.out.println("Task #" + previous.id);
        	System.out.println("Winner: " + winner + " for " + bids[0] + " vs " + bids[1]);	
    	}
    }
	
	protected void logResults(List<Plan> plans, TaskSet tasks) {
		
		logMessage("Tasks: " + tasks);
		logMessage("Costs: " + costOfTasks(plans));
		logMessage("Cost: " + costOfMoves(plans));
		logMessage("Payments: " + rewardOfTasks(tasks));
		logMessage("Tasks: " + tasks);
		logMessage("Total Reward: " + (rewardOfTasks(tasks) - costOfMoves(plans)));

	}
	
	protected void logMessage(String message) {
		if (log) {
			System.out.println("Agent #" + id + " - " + message);
		}
 	}
	
	static class CostCounter implements ActionHandler<Void> {

		private City current;
		
		private Set<Task> currentTasks;
		private Map<Task, Double> cost;
		private int costPerKm;

		Map<Task, Double> costOfTasks(City initial, Iterable<Action> actions, int costPerKm) {
			this.current = initial;
			this.costPerKm = costPerKm;
			currentTasks = new HashSet<Task>();
			cost = new HashMap<Task, Double>();
			
			for (Action action : actions)
				action.accept(this);
			
			return cost;
		}

		@Override
		public Void deliver(Task task) {
			currentTasks.remove(task);
			return null;
		}

		@Override
		public Void moveTo(City target) {
			double length = current.distanceTo(target);
			
			for (Task task: currentTasks) {
				double current = cost.get(task);
				double newCost = current + (length * costPerKm / (double) currentTasks.size());
				System.out.println("Current is " + current);
				cost.put(task, newCost);
			}
			
			current = target;
			return null;
		}

		@Override
		public Void pickup(Task task) {
			cost.put(task, 0.0);
			currentTasks.add(task);
			return null;
		}
	}
}


