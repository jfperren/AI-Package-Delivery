package solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Collections;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.topology.Topology.City;

public class ConstraintOptimizationSolver {

	private static final boolean LOG_RESULTS = false;

	private List<VehicleLabel> vehicles = new ArrayList<VehicleLabel>();
	private List<PickupLabel> pickups = new ArrayList<PickupLabel>();
	private List<DeliveryLabel> deliveries = new ArrayList<DeliveryLabel>();

	private Random random;

	/**
	 * Represent a constraint optimization solver for the pickup-and-delivery problem.
	 */
	public ConstraintOptimizationSolver(List<Vehicle> vehicles, Set<Task> tasks, int seed) {

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

	/**
	 * Select a random vehicle within the list, making sure its capacity is at
	 * least a given number. Used in the context of generating initial solutions.
	 */
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

	/**
	 * Create an initial assignment by iteratively going over the set of task and, for each,
	 * assigning it to a random vehicle that will naively deliver before its other tasks.
	 */
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

	/**
	 * Run the solver for the assigned configuration.
	 *
	 * @param timeout Number of miliseconds to run the solver for
	 * @param p Probability of selecting a worse neighbor
	 * @param neighborhoodSize Number of neighbors to create in each step
	 * @param nReset Number of iterations before which resetting when stuck in
	 * 		  local minimum.
	 * @return The best assignment found in the given time.
	 */
	@SuppressWarnings("unused")
	public List<Plan> solve(double timeout, double p, int neighborhoodSize, int nReset) {

		// Create a random assignment and use it a global best for now.
		Assignment assignment = initialAssignment(random.nextInt());
		Assignment globalBestAssignment = assignment;

		// We calculate the cost and store it as current and global best.
		double cost = assignment.cost();
		double globalBestCost = cost;
		double currentBestCost = cost;

		long now = System.currentTimeMillis();

		int resetCounter = 0;
		int counter = 0;

		while (System.currentTimeMillis() - now < timeout) {

			Assignment nextAssignment = assignment.chooseNext(neighborhoodSize);
			double nextAssignmentCost = nextAssignment.cost();

			// We always select the next assignment if its cost is lower or equal,
			// otherwise we only do it with probability p.
			if (nextAssignmentCost <= cost || random.nextDouble() < p) {
				assignment = nextAssignment;
				cost = nextAssignmentCost;
			}

			// If the cost is a new current minimum, we restart the reset counter.
			if (cost < currentBestCost) {
				currentBestCost = cost;
				resetCounter = 0;
			} else {
				resetCounter++;
			}

			// If the cost is a new global minimum, we also store it.
			if (cost < globalBestCost) {
				globalBestAssignment = assignment;
				globalBestCost = cost;
			}

			// If we reached the counter, we reset to a new initialAssignment
			if (resetCounter > nReset) {
				assignment = initialAssignment(random.nextInt());
				cost = assignment.cost();
				currentBestCost = cost;
				resetCounter = 0;
			}

			// Log as we go if required
			if (LOG_RESULTS && counter % 10 == 0) {
				System.out.println(counter + "|" + resetCounter + "|" + cost + "|" + globalBestCost + "|" + assignment.hashCode());
			}

			counter++;
		}

		return globalBestAssignment.getPlans();
	}

	/**
	 * Represent an Assignment of variables within the ConstraintOptimizationProblem.
	 */
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

				result += "done\n";
			}

			return result;
		}

		@Override
		public int hashCode() {
			return toString().hashCode();
		}

		/**
		 * Create the logist plan corresponding to the Assignment of variables.
		 */
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


		/**
		 * Generate a neighborhood of a given size and return the best assignment
		 * within that neighborhood.
		 */
		public Assignment chooseNext(int size) {

			double bestCost = Double.POSITIVE_INFINITY;
			List<Assignment> bestAssignment = new ArrayList<Assignment>();


			// It is rare, but sometimes we get unlucky and generate only bad
			// neighbors (especially with low size). Therefore, we simply iterate
			// until we find a neighborhood that has at least one correct assignment.
			do {
				for (Assignment assignment: neighbors(size)) {

					double cost = assignment.cost();

					if (cost == Double.POSITIVE_INFINITY) {
						continue;
					}

					if (cost <= bestCost) {

						bestCost = cost;
						bestAssignment.clear();
						bestAssignment.add(assignment);
					} else if (cost == bestCost) {
						bestAssignment.add(assignment);
					}
				}
			} while (bestAssignment.size() == 0);

			return bestAssignment.get(random.nextInt(bestAssignment.size()));
		}

		/**
		 * 	Calculate the total cost of a given assignment. If the assignment does not satisfy the
		 *  constraints, we simply return Double.POSITIVE_INFINITY.
		 *
		 *  Note
		 *  ----
		 *  Because of the implementation of successors as a HashMap, there is already a number of
		 *  constraints which are already implicitely satisfied (e.g. X_time, the fact that two
		 *  variables are not at the same position, etc..). Moreover, we  do not need to explicitely
		 *  check each one of the constraints defined mathematically, especially as this would be
		 *  O(n^2). Instead, we iterate over the whole plan and make  sure that it is consistent as we
		 *  go, which effectively does the same but in O(n).
		 */
		public double cost() {

			double cost = 0;

			// For each packet, this map contains 0 if it is not picked up, 1 if it is and 2 if delivered.
			HashMap<Integer, Integer> deliveryStatus = new HashMap<Integer, Integer>();

			for (Label task: pickups) {
				deliveryStatus.put(task.taskId(), 0);
			}

//			System.out.println("SUCCESSORS BEFORE: " + successors);
			
			for (Label vehicle: vehicles) {

				// At the start, the vehicle has a load of 0.
				int load = 0;

				for (Label action = vehicle; !action.isEnd(); action = successors.get(action)) {

					// Increase / Decrease weight
					load += action.weightChange();

					// Add cost related to the travel
					cost += action.distanceTo(successors.get(action)) * vehicle.costPerKm();

					if (action.isPickup()) {

						// Check that the task is available
//						System.out.println("STATUS: " + deliveryStatus);
//						System.out.println("SUCCESSORS: " + successors);
//						System.out.println("TASK ID: " + action.taskId());
						if (deliveryStatus.get(action.taskId()) != 0) {
							return Double.POSITIVE_INFINITY;
						}

						// Check that we do not go over capacity
						if (load > vehicle.capacity()) {
							return Double.POSITIVE_INFINITY;
						}

						deliveryStatus.put(action.taskId(), 1);

					} else if (action.isDelivery()) {

						// Check that the task is picked up
						if (deliveryStatus.get(action.taskId()) != 1) {
							return Double.POSITIVE_INFINITY;
						}

						deliveryStatus.put(action.taskId(), 2);
					}
				}
			}

			// Check that all tasks have been delivered.
			for (int taskId: deliveryStatus.keySet()) {
				if (deliveryStatus.get(taskId) != 2) {
					return Double.POSITIVE_INFINITY;
				}
			}

			return cost;
		}

		/**
		 * Generate a random set of neighbors of a given size. The neighbors are
		 * generated by randomly selecting a task, and then randomly selecting
		 * new positions for its pickup and delivery positions {size} times.
		 */
		public List<Assignment> neighbors(int size) {

			Label beforePickup;
			Label beforeDelivery = null;

			List<Label> allLabels = new ArrayList<Label>();
			allLabels.addAll(successors.keySet());

			// We select a random "pickup" label in the list
			do {
				beforePickup = allLabels.get(random.nextInt(allLabels.size()));
			} while (!successors.get(beforePickup).isPickup());

			// Then, we iterate on the successors until we find its corresponding delivery action
			for (Label label = beforePickup; !successors.get(label).isEnd(); label = successors.get(label)) {

				Label deliveryCandidate = successors.get(label);

				if (deliveryCandidate.isDelivery() && deliveryCandidate.taskId() == successors.get(beforePickup).taskId()) {
					beforeDelivery = label;
				}
			}

			// Now, we will randomly pick new positions out of the total
			// set of possible positions, without replacement. Note that
			// we do this by shuffling the list of labels once and then
			// dequeue the current last item for as long as we want. This
			// ensures that the deletion of the randomly selected item is
			// done in O(1).
			List<Label> availableLabels = new ArrayList<Label>();
			availableLabels.addAll(successors.keySet());
			Collections.shuffle(availableLabels, random);

			List<Assignment> neighbors = new ArrayList<Assignment>();

			for (int i = 0; i < size; i++) {

				// Stop if we don't have any unvisited position
				if (availableLabels.isEmpty()) {
					return neighbors;
				}

				// Dequeue the last position in the list
				Label newBeforePickup = availableLabels.get(availableLabels.size() - 1);

				// Generate all assignments where the pickup is at the selected position
				// and the delivery is after it.
				for (Label newBeforeDelivery = newBeforePickup; !successors.get(newBeforeDelivery).isEnd(); newBeforeDelivery = successors.get(newBeforeDelivery)) {

					neighbors.add(move(beforePickup, beforeDelivery, newBeforePickup, newBeforeDelivery));
				}

				availableLabels.remove(availableLabels.size() - 1);
			}

			return neighbors;
		}

		/**
		 * Generate the assignment resulting from moving the pickup and delivery actions to the
		 * new positions.
		 */
		public Assignment move(Label beforePickup, Label beforeDelivery, Label newBeforePickup, Label newBeforeDelivery) {

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

			// We save the next nodes after the insertion

			Label newAfterPickup = newPlan.get(newBeforePickup);
			Label newAfterDelivery = newPlan.get(newBeforeDelivery);

			// Then, we add them back in this new location

			newPlan.put(newBeforePickup, pickup);
			newPlan.put(delivery, newAfterDelivery);

			if (newBeforePickup == newBeforeDelivery) {
				newPlan.put(pickup, delivery);
			} else {
				newPlan.put(pickup, newAfterPickup);
				newPlan.put(newBeforeDelivery, delivery);
			}

			return new Assignment(newPlan);
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
