package company;

import java.util.List;
import java.util.Random;

import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;


abstract class AbstractCompany implements AuctionBehavior {
	
	protected Topology topology;
	protected TaskDistribution distribution;
	protected Agent agent;
	protected Random random;
	
	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		
		long seed = -9019554669489983951L * agent.id();
		this.random = new Random(seed);
		
	}
	
    protected double costOfMoves(List<Plan> plans) {
    	
    	double reward = 0;
        
        for (int k = 0; k < agent.vehicles().size(); k++) {
        	reward += plans.get(k).totalDistance() * agent.vehicles().get(k).costPerKm();
        }
        
        return reward;
    }
    
    protected double rewardOfTasks(TaskSet tasks) {
    	
    	double reward = 0;
    	
    	for (Task task: tasks) {
    		reward += task.reward;
    	}
    	
    	return reward;
    }
	
	protected void logResults(List<Plan> plans, TaskSet tasks) {
		
		System.out.println("-- Agent " + agent.id() + " --");
		System.out.println("Tasks: " + tasks);
		System.out.println("Cost: " + costOfMoves(plans));
		System.out.println("Payments: " + rewardOfTasks(tasks));
		System.out.println("Total Reward: " + (rewardOfTasks(tasks) - costOfMoves(plans)));
	}
}


