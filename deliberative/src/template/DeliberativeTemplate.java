package template;

/* import table */
import logist.simulation.Vehicle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Action;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import template.State.Tuple;


/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeTemplate implements DeliberativeBehavior {
	
	enum Algorithm { BFS, ASTAR, NAIVE }
	enum Stop { FIRST, BEST }
	
	/* Environment */
	Topology topology;
	TaskDistribution td;
	
	/* the properties of the agent */
	Agent agent;
	int capacity;

	/* the planning class */
	Algorithm algorithm;
	
	/* Stop */
	Stop stop;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		
		// initialize the planner
		int capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
		String stopName = agent.readProperty("stop", String.class, "BEST");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
		stop = Stop.valueOf(stopName.toUpperCase());
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
			plan = AStarPlan(vehicle, tasks, stop, new Logger());
			break;
		case BFS:
			plan = BFSPlan(vehicle, tasks, stop, new Logger());
			break;
		case NAIVE:
			plan = naivePlan(vehicle, tasks, new Logger());
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
		return plan;
	}
	
	private Plan naivePlan(Vehicle vehicle, TaskSet tasks, Logger logger) {
		
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);
		
		logger.initialize("Naive");

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path()) {
				plan.appendMove(city);
				logger.increment();
			}

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		
		logger.logResults(plan);
		
		return plan;
	}
	
	private Plan BFSPlan(Vehicle vehicle, TaskSet tasks, Stop stop, Logger logger) {
		
		
		State initialState = new State(vehicle, tasks);
		logger.initialize("BFS (" + stop + ")");
		
		Map<State, State> parents = new HashMap<State, State>();
		Map<State, Action> causes = new HashMap<State, Action>();
		
		Map<State, Double> costs = new HashMap<State, Double>(); 
		costs.put(initialState, 0.0);
		
		Double bestCost = Double.POSITIVE_INFINITY;
		State goal = null;
		
		Queue<State> queue = new ArrayDeque<State>();
		queue.add(initialState);
		
		do {
			State state = queue.poll();
			Double cost = costs.get(state);
			
			logger.increment();
			
			// If the state is a final state, and its cost is lower than
			// any previous plan so far, we mark it as new goal state.
			if (state.isFinal() && costs.get(state) < bestCost) {
				bestCost = costs.get(state);
				goal = state;
				
				if (stop == Stop.FIRST) {
					break;
				}
			}
			
			for (Tuple<State, Action> tuple: state.nextStates()) {
				
				State child = tuple.x;
				Action action = tuple.y;
		
				Double distance = state.currentCity.distanceTo(child.currentCity);
				Double childCost = cost + distance * vehicle.costPerKm();
				
				// If the cost at the child state is higher than the total cost of the 
				// best plan found so far, we can simply skip this child.
				if (childCost > bestCost) {
					continue;
				}
				
				// Here, we should check if the state was already visited. If this is the case,
				// we see what was the cost last time this state was visited. If we get a lower
				// cost this time, we continue. Otherwise, it's a dead-end so we can skip it.
				if (costs.containsKey(child) && childCost >= costs.get(state)) { // The child state was already visited.
					continue;
				}
				
				// Here, we write the action that caused this state, its cost and its parent.
				// This is all so that we can retrieve the information when reconstructing
				// the plan from the last state without having to keep a copy of all the
				// parents in every single node.
				parents.put(child, state);
				costs.put(child, childCost);
				causes.put(child, action);
				
				// Enqueue the child
				queue.add(child);
			}
		} while (!queue.isEmpty());
		
		// At this point, we have the goal state with the lowest cost possible in the graph.
		// We can simply reconstruct the list of actions that led to this state by iterating
		// over all the parents of this state
		
		Plan plan = reconstructPlan(goal, vehicle.getCurrentCity(), causes, parents);
				
		logger.logResults(plan);
		
		return plan;
	}
	
	public Plan AStarPlan(Vehicle vehicle, TaskSet tasks, Stop stop, Logger logger) {
		
		State initialState = new State(vehicle, tasks);
		logger.initialize("A-Star (" + stop + ")");
		
		Map<State, State> parents = new HashMap<State, State>();
		Map<State, Action> causes = new HashMap<State, Action>();
		
		final Map<State, Double> f = new HashMap<State, Double>();
		f.put(initialState, initialState.heuristic());
		
		final Map<State, Double> g = new HashMap<State, Double>();
		g.put(initialState, 0.0);
		
		Double bestCost = Double.POSITIVE_INFINITY;
		State goal = null;
		
		Queue<State> queue = new PriorityQueue<State>(new Comparator<State>() {
			
			@Override
			public int compare(State lhs, State rhs) {
				return f.get(lhs).compareTo(f.get(rhs));
			}
		});
		
		queue.add(initialState);
		
		int count = 0;
		
		do {
			State state = queue.poll();
			Double cost = g.get(state);
			
			logger.increment();
			
			// If the state is a final state, and its cost is lower than
			// any previous plan so far, we mark it as new goal state.
			if (state.isFinal( )&& g.get(state) < bestCost) {
				bestCost = g.get(state);
				goal = state;
				
				if (stop == Stop.FIRST) {
					break;
				}
			}
			
			for (Tuple<State, Action> tuple: state.nextStates()) {
				
				State child = tuple.x;
				Action action = tuple.y;
		
				Double distance = state.currentCity.distanceTo(child.currentCity);
				Double childCost = cost + distance * vehicle.costPerKm();
				
				// If the cost at the child state is higher than the total cost of the 
				// best plan found so far, we can simply skip this child.
				if (childCost > bestCost) {
					continue;
				}
				
				// Here, we should check if the state was already visited. If this is the case,
				// we see what was the cost last time this state was visited. If we get a lower
				// cost this time, we continue. Otherwise, it's a dead-end so we can skip it.
				if (g.containsKey(child)) { // The child state was already visited.
					continue;
				}
				
				// Here, we write the action that caused this state, its cost and its parent.
				// This is all so that we can retrieve the information when reconstructing
				// the plan from the last state without having to keep a copy of all the
				// parents in every single node.
				parents.put(child, state);
				g.put(child, childCost);
				f.put(child, childCost + child.heuristic());
				causes.put(child, action);
				
				// Enqueue the child
				queue.add(child);
			}
		} while (!queue.isEmpty() && count < 100000);
		
		
		Plan plan = reconstructPlan(goal, vehicle.getCurrentCity(), causes, parents);
		
		logger.logResults(plan);
		
		return plan;
	}
	
	private Plan reconstructPlan(State goal, City current, Map<State, Action> causes, Map<State, State> parents) {
		
		Plan plan = new Plan(current);
		
		LinkedList<Action> actions = new LinkedList<Action>(); 
		State state = goal;
		
		do {
			Action cause = causes.get(state);
			actions.addFirst(cause);
			state = parents.get(state);
		} while (causes.get(state) != null);
		
		for (Action action: actions) {
			plan.append(action);
		}
		
		return plan;
	}

	@Override
	public void planCancelled(TaskSet carriedTasks) {
		
		if (!carriedTasks.isEmpty()) {
			// This cannot happen for this simple agent, but typically
			// you will need to consider the carriedTasks when the next
			// plan is computed.
		}
	}
}
