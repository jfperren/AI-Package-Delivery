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

public class GreedyBehavior implements ReactiveBehavior {

	private int numActions;
	private Agent myAgent;

	private Map<City, City> best_move = new HashMap<>();

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.numActions = 0;
		this.myAgent = agent;

		for (City start : topology.cities()){
			double max_expect = 0;
			City best_city = start.randomNeighbor(new Random());
			for (City dest : start.neighbors()){
				if (td.probability(start, dest)*td.reward(start, dest) > max_expect){
					best_city = dest;
				}
			}
			best_move.put(start, best_city);
		}
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;

		if (availableTask == null) {
			City currentCity = vehicle.getCurrentCity();
			action = new Move(best_move.get(currentCity));
		} else {
			action = new Pickup(availableTask);
		}
		
		if (numActions >= 1) {
			System.out.println(myAgent.name() + ": The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
}