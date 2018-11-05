package template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Collections;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

public class ConstraintOptimizationProblem {
	
	private List<VehicleLabel> vehicles = new ArrayList<VehicleLabel>();
	private List<PickupLabel> pickups = new ArrayList<PickupLabel>();
	private List<DeliveryLabel> deliveries = new ArrayList<DeliveryLabel>();
	
	private Random random;
	
	public ConstraintOptimizationProblem(List<Vehicle> vehicles, TaskSet tasks, int seed) {
		
		random = new Random(seed);
		
		for (Vehicle vehicle: vehicles) {
			this.vehicles.add(new VehicleLabel(vehicle));
		}
		
		Iterator<Task> iterator = tasks.iterator();
		
		while (iterator.hasNext()) {
			Task task = iterator.next();
			
			pickups.add(new PickupLabel(task));
			deliveries.add(new DeliveryLabel(task));
		}
	}
	
	private VehicleLabel randomVehicleWithCapacityAtLeast(int capacity) {
		
		List<VehicleLabel> capableVehicles = new ArrayList<VehicleLabel>();
		
		for (VehicleLabel vehicle: vehicles) {
			if (vehicle.capacity() >= capacity) {
				capableVehicles.add(vehicle);
			}
		}
		
		int index = (int) Math.floor(random.nextInt(capableVehicles.size()));
		
		return capableVehicles.get(index);
	}
	
	private List<Integer> randomIndices(int max) {
		
		List<Integer> indices = new ArrayList<Integer>();
		
		for (int i = 0; i < max; i++) {
			indices.add(i);
		}
		
		Collections.shuffle(indices, random);
		return indices;
	}
	
	public Assignment initialAssignment(int seed) {
		
		Map<Label, Label> next = new HashMap<Label, Label>();
		
		for (Label vehicle: vehicles) {
			next.put(vehicle, new EndLabel());
		}
		
		for (int i: randomIndices(pickups.size())) {
			
			Label pickup = pickups.get(i);
			Label delivery = deliveries.get(i);
			Label vehicle = randomVehicleWithCapacityAtLeast(pickup.weightChange());
			
			Label current = next.get(vehicle);
			
			next.put(vehicle, pickup);
			next.put(pickup , delivery);
			next.put(delivery, current);
		}
		
		return new Assignment(next);
	}
	
	public List<Plan> solve(double timeout, double p) {
		
		Assignment assignment = initialAssignment(random.nextInt());
		Assignment globalBestAssignment = assignment;	
		
		double cost = assignment.cost();
		double globalBestCost = cost;	
		double localBestCost = cost;
		
		long now = System.currentTimeMillis();
		
		int resetCounter = 0;
		int counter = 0;
		
		while (System.currentTimeMillis() - now < 0.8 * timeout) {
			
			Assignment nextAssignment = assignment.chooseNext(random);
			double nextAssignmentCost = nextAssignment.cost();
			
			double p_better = 1.0;
			double p_worse = 3.0;
			double rnd = random.nextDouble();
			
			if ((nextAssignmentCost <= cost && rnd < p_better) || rnd < p_worse) {
				assignment = nextAssignment;
				cost = nextAssignmentCost;
			}
			
			if (cost < localBestCost) {
				localBestCost = cost;
				resetCounter = 0;
			} else {
				resetCounter++;
			}

			if (cost < globalBestCost) {
				globalBestAssignment = assignment;
				globalBestCost = cost;
			}
			
			if (resetCounter > 900) {
				assignment = initialAssignment(random.nextInt());
				cost = assignment.cost();
				localBestCost = cost;
				resetCounter = 0;
			}
			
			if (counter % 10 == 0) {
				System.out.println(counter + "|" + resetCounter + "|" + cost + "|" + globalBestCost + "|" + assignment.hashCode() + "|" + p_worse);
			}	
			
			counter++;
		}
		
		return globalBestAssignment.getPlans();
	}
	
	class Assignment {
 			
		// An assignment is uniquely defined by the mapping of successor states. Every other variable
		// (time, load, vehicle) is redundant and, although it is required in the mathematical version,
		// it adds unnecessary complexity to the algorithmic version. 
		public Map<Label, Label> successors;
		
		private Assignment(Map<Label, Label> next) {
			this.successors = next;
		}
		
		@Override
		public String toString() {
			
			String result = "- Plan -\n";
			
			for (Label vehicle: vehicles) {
				for (Label action = vehicle; !action.isEnd(); action = successors.get(action)) {
					result = result + action +  " - ";
				}
				
				result += "Done \n";
			}
			
			return result;
		}
		
		@Override
		public int hashCode() {
			return toString().hashCode();
		}
		
		// ------
		
		public List<Plan> getPlans() {
			
			List<Plan> plans = new ArrayList<Plan>();
			
			for (Label vehicle: vehicles) {
				
				Plan plan = new Plan(vehicle.getCity());
				
				for (Label action = vehicle; !action.isEnd(); action = successors.get(action)) {
					
					if (action.isPickup()) {
						plan.appendPickup(action.task());
					} else if (action.isDelivery()) {
						plan.appendDelivery(action.task());
					}
					
					if (!successors.get(action).isEnd()) {
						for (City city: action.pathTo(successors.get(action))) {
							plan.appendMove(city);
						}
					}
				}
				
				plans.add(plan);
			}
			
			return plans;
		}
		
		public Assignment randomNeighbor() {
			List<Assignment> neighbors = neighbors();
			return neighbors.get(random.nextInt(neighbors.size()));
		}
		
		public Assignment chooseNext(Random random) {
						
			double bestCost = Double.POSITIVE_INFINITY;
			List<Assignment> bestAssignment = new ArrayList<Assignment>();
			
			for (Assignment assignment: neighbors()) {
				
				double cost = assignment.cost();
				
				if (cost <= bestCost) {
										
					bestCost = cost;
					bestAssignment.clear();
					bestAssignment.add(assignment);
				} else if (cost == bestCost) {
					bestAssignment.add(assignment);
				}
			}
			
			return bestAssignment.get(random.nextInt(bestAssignment.size()));
		}
		
		/** Calculate the total cost of a given assignment. If the assignment does not satisfy the
		 *  constraints, we simply return Double.POSITIVE_INFINITY.
		 */
		public double cost() {
			
			// Important notes
			// ---------------
			//
			// Because of the implementation of successors as a HashMap, there is already a number of 
			// constraints which are already implicitely satisfied.
						
			// Initialize cost to 0.
			double cost = 0;
			
			
			HashMap<Integer, Integer> deliveryStatus = new HashMap<Integer, Integer>();
			
			for (int i = 0; i < pickups.size(); i++) {
				deliveryStatus.put(i, 0);
			}
			
			for (Label vehicle: vehicles) {
				
				int load = 0;
				
				for (Label action = vehicle; !action.isEnd(); action = successors.get(action)) {
					
					load += action.weightChange();
					cost += action.distanceTo(successors.get(action)) * vehicle.costPerKm();
										
					if (action.isPickup()) {
						
						if (deliveryStatus.get(action.taskId()) != 0) {
							return Double.POSITIVE_INFINITY;
						}
						
						if (load > vehicle.capacity()) {
							return Double.POSITIVE_INFINITY;
						}
						
						deliveryStatus.put(action.taskId(), 1);
						
					} else if (action.isDelivery()) {
						
						if (deliveryStatus.get(action.taskId()) != 1) {
							return Double.POSITIVE_INFINITY;
						}
						
						deliveryStatus.put(action.taskId(), 2);	
					}
				}
			}
			
			// Here, we check that all tasks have been delivered. 
			for (int taskId: deliveryStatus.keySet()) {
				if (deliveryStatus.get(taskId) != 2) {
					return Double.POSITIVE_INFINITY;
				}
			}
			
			return cost;
		}
		
		public List<Assignment> neighbors() {
			
			List<Assignment> neighbors = new ArrayList<Assignment>();
			
			
			Label randomPickup = pickups.get(random.nextInt(pickups.size()));
			
			for (Label vehicle: vehicles) {
				
				for (Label beforePickup = vehicle; !successors.get(beforePickup).isEnd(); beforePickup = successors.get(beforePickup)) {
					
					Label pickupCandidate = successors.get(beforePickup);
					
					if (pickupCandidate == randomPickup) {
						
						for (Label beforeDelivery = successors.get(beforePickup); !successors.get(beforeDelivery).isEnd(); beforeDelivery = successors.get(beforeDelivery)) {
							
							Label deliveryCandidate = successors.get(beforeDelivery);
							
							if (deliveryCandidate.isDelivery() && deliveryCandidate.taskId() == pickupCandidate.taskId()) {
								
								neighbors.addAll(move(beforePickup, beforeDelivery));
							}
						}
					}	
				}
			}
			
			return neighbors;
		}
	
		public List<Assignment> move(Label beforePickup, Label beforeDelivery) {
			
			List<Assignment> assignments = new ArrayList<Assignment>();
			
			Label pickup = successors.get(beforePickup);
			Label delivery = successors.get(beforeDelivery);
			Label afterPickup = successors.get(pickup);
			Label afterDelivery = successors.get(delivery);
			
			// First, we remove Pickup and Delivery from the plan
			
			Map<Label, Label> newPlan = new HashMap<Label, Label>();
			newPlan.putAll(successors);
			
			if (pickup == beforeDelivery) {
				newPlan.put(beforePickup, afterDelivery);
			} else {
				newPlan.put(beforePickup, afterPickup);
				newPlan.put(beforeDelivery, afterDelivery);
			}
			
			for (Label vehicle: vehicles) {
				
				int load = 0;
				
				for (Label newBeforePickup = vehicle; !newBeforePickup.isEnd(); newBeforePickup = newPlan.get(newBeforePickup)) {
					
					load += newBeforePickup.weightChange();
					
					if (load + pickup.weightChange() < vehicle.capacity()) {
						
						for (Label newBeforeDelivery = newBeforePickup; !newBeforeDelivery.isEnd(); newBeforeDelivery = newPlan.get(newBeforeDelivery)) {			
							
							if (newBeforeDelivery != beforeDelivery || newBeforePickup != beforePickup) {
							
								Map<Label, Label> newPlan2 = new HashMap<Label, Label>();
								newPlan2.putAll(newPlan);
								
								// We save the next nodes after the insertion
								
								Label newAfterPickup = newPlan2.get(newBeforePickup);
								Label newAfterDelivery = newPlan2.get(newBeforeDelivery);
								
								// Then, we add them back in this new location
								
								newPlan2.put(newBeforePickup, pickup);
								newPlan2.put(delivery, newAfterDelivery);
								
								if (newBeforePickup == newBeforeDelivery) {
									newPlan2.put(pickup, delivery);
								} else {
									newPlan2.put(pickup, newAfterPickup);
									newPlan2.put(newBeforeDelivery, delivery);
								}
							
							
								assignments.add(new Assignment(newPlan2));
							}
						}
					}
				}
			}
			
			return assignments;		
		}
	}
	
	// MARK: - Label Interface & Classes
	
	abstract class Label {
		
		abstract public City getCity();
		
		public int weightChange() {
			return 0;
		}
		
		public int costPerKm() {
			throw new UnsupportedOperationException();
		}
		
		public int capacity() {
			throw new UnsupportedOperationException();
		}
		
		public boolean isPickup() {
			return false;
		}
		
		public boolean isDelivery() {
			return false;
		}
		
		public boolean isVehicle() {
			return false;
		}
		
		public boolean isEnd() { 
			return false;
		}
		
		public int taskId() {
			throw new UnsupportedOperationException();
		}
		
		public Task task() {
			throw new UnsupportedOperationException();
		}
		
		public double distanceTo(Label next) {
			if (next.isEnd()) { return 0; }
			
			return getCity().distanceTo(next.getCity());
		}
		
		public List<City> pathTo(Label next) {
			if (next.isEnd()) { return new ArrayList<City>(); }
			
			return getCity().pathTo(next.getCity());
		}
	}
	
	class VehicleLabel extends Label {
		
		private Vehicle vehicle;
		
		private VehicleLabel(Vehicle vehicle) {
			this.vehicle = vehicle;
		}
		
		public City getCity() {
			return vehicle.homeCity();
		}
		
		public int costPerKm() {
			return vehicle.costPerKm();
		}
		
		public int capacity() {
			return vehicle.capacity();
		}
		
		public boolean isVehicle() {
			return true;
		}
		
		@Override
		public String toString() {
			return "Vehicle(" + vehicle.id() + ")";
		}
	}
	
	class PickupLabel extends Label { 
		
		private Task task;
		
		private PickupLabel(Task task) {
			this.task = task;
		}
		
		public City getCity() {
			return task.pickupCity;
		}
		
		public int weightChange() {
			return task.weight;
		}
		
		public boolean isPickup() {
			return true;
		}
		
		public int taskId() {
			return task.id;
		}
		
		public Task task() {
			return task;
		}
		
		@Override
		public String toString() {
			return "Pickup(" + task.id + ")";
		}
	}
	
	class DeliveryLabel extends Label { 
		
		private Task task;
		
		private DeliveryLabel(Task task) {
			this.task = task;
		}
		
		public City getCity() {
			return task.deliveryCity;
		}
		
		public int weightChange() {
			return -task.weight;
		}
		
		public boolean isDelivery() {
			return true;
		}

		public int taskId() {
			return task.id;
		}
		
		public Task task() {
			return task;
		}
		
		@Override
		public String toString() {
			return "Delivery(" + task.id + ")";
		}
	}
	
	class EndLabel extends Label { 
		
		private EndLabel() { }
		
		public City getCity() {
			return null;
		}
		
		public int weightChange() {
			return 0;
		}
		
		public boolean isEnd() {
			return true;
		}
		
		@Override
		public String toString() {
			return "End";
		}
	}
}
