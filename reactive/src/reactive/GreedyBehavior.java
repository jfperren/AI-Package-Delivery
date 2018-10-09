package reactive;

import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * A greedy agent behavior that always selects a task if available. If there is no
 * task, it will simply move to the neighbor that contains tasks with highest expected
 * rewards.
 * 
 * @author Julien Perrenoud & Pierre-Antoine Desplaces.
 *
 */
public class GreedyBehavior implements ReactiveBehavior {

	private Agent myAgent;
	private BehaviorLogger logger;

	private Map<City, City> bestMove = new HashMap<>();

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		this.myAgent = agent;
		this.logger = new BehaviorLogger();

		for (City start : topology.cities()){
			
			double maxValue = Double.NEGATIVE_INFINITY;
			City bestCity = null;
			
			for (City destination : start.neighbors()){
				if (td.probability(start, destination) * td.reward(start, destination) > maxValue){
					bestCity = destination;
				}
			}
			
			bestMove.put(start, bestCity);
		}
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;

		if (availableTask == null) {
			City currentCity = vehicle.getCurrentCity();
			action = new Move(bestMove.get(currentCity));
		} else {
			action = new Pickup(availableTask);
		}
		
		logger.logProfit(myAgent);
		
		return action;
	}
}