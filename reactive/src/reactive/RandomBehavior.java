package reactive;

import java.util.Random;

import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;


public class RandomBehavior implements ReactiveBehavior {

	public static final double DEFAULT_P_PICKUP = 0.95;
	
	private Random random;
	private double pPickup;
	private Agent myAgent;
	private BehaviorLogger logger;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		Double discount = agent.readProperty("p-pickup", Double.class, DEFAULT_P_PICKUP);
		this.pPickup = Math.min(Math.max(discount, 0.0), 1.0);
		
		this.random = new Random();
		this.myAgent = agent;
		this.logger = new BehaviorLogger();
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;

		if (availableTask == null || random.nextDouble() > pPickup) {
			City currentCity = vehicle.getCurrentCity();
			action = new Move(currentCity.randomNeighbor(random));
		} else {
			action = new Pickup(availableTask);
		}
		
		logger.logProfit(myAgent);
		
		return action;
	}
}