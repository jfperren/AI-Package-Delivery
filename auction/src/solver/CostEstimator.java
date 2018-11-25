package solver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

public class CostEstimator {
	
	TaskDistribution distribution;
	Topology topology;
	Set<Task> tasks = new HashSet<Task>();
	double averageCost = 0;

	
	public CostEstimator(TaskDistribution distribution, Topology topology) {
		
		this.topology = topology;
		this.distribution = distribution;
		
	}
	
	public void setup() {
		
		int id = 0;
		
		for (City source: topology) {
			
			for (City destination: topology) {
				
				int weight = distribution.weight(source, destination);
				tasks.add(new Task(id++, source, destination, 0, weight));
			}
		}
		
		for (Task task: tasks) {
			averageCost += probability(task) * weightedJointCost(task);
		}		
	}
	
	public double probability(City from, City to) {
		return distribution.probability(from, to) / topology.cities().size();
	}
	
	public double probability(Task t) {
		return probability(t.pickupCity, t.deliveryCity);
	}
	
	public double weightedJointCost(Task t) {
		
		double cost = 0;
		
		for (Task t2: tasks) {
			cost += probability(t2) * jointCost(t, t2);
		}
		
		return cost;
	}
	
	public double coeff(Task t) {
		return weightedJointCost(t) / averageCost;
	}
	
	/**
	 * Manually calculate the joint cost between two tasks by considering all
	 * 6 possible paths and returning the minimum cost.
	 * 
	 * @param t1 the first task
	 * @param t2 the second task
	 * @return the minimum cost for delivering both tasks
	 */
	public double jointCost(Task t1, Task t2) {
		
		List<List<City>> plans = new ArrayList<List<City>>();
		
		plans.add(new ArrayList<City>());
		plans.add(new ArrayList<City>());
		plans.add(new ArrayList<City>());
		plans.add(new ArrayList<City>());
		plans.add(new ArrayList<City>());
		plans.add(new ArrayList<City>());
		
		//
		
		plans.get(0).add(t1.pickupCity);
		plans.get(1).add(t1.pickupCity);
		plans.get(2).add(t1.pickupCity);
		plans.get(3).add(t2.pickupCity);
		plans.get(4).add(t2.pickupCity);
		plans.get(5).add(t2.pickupCity);
		
		//
		
		plans.get(0).add(t2.pickupCity);
		plans.get(1).add(t2.pickupCity);
		plans.get(2).add(t1.deliveryCity);
		plans.get(3).add(t1.pickupCity);
		plans.get(4).add(t1.pickupCity);
		plans.get(5).add(t2.deliveryCity);
		
		//
		
		plans.get(0).add(t1.deliveryCity);
		plans.get(1).add(t2.deliveryCity);
		plans.get(2).add(t2.pickupCity);
		plans.get(3).add(t1.deliveryCity);
		plans.get(4).add(t2.deliveryCity);
		plans.get(5).add(t1.pickupCity);
		
		//
		
		plans.get(0).add(t2.deliveryCity);
		plans.get(1).add(t1.deliveryCity);
		plans.get(2).add(t2.deliveryCity);
		plans.get(3).add(t2.deliveryCity);
		plans.get(4).add(t1.deliveryCity);
		plans.get(5).add(t1.deliveryCity);
		
		double minCost = Double.POSITIVE_INFINITY;

		for (List<City> cities: plans) {
			
			double cost = 0;
			City current = null;
			
			for (City city: cities) {
				
				if (current != null) {
					cost += current.distanceTo(city);
				}
				
				current = city;
			}
			
			if (cost < minCost) {
				minCost = cost;
			}
		}
		
		return minCost;
	}
}
