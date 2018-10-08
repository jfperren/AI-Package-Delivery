package reactive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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


public class RLABehavior implements ReactiveBehavior {
		
	public static final double DEFAULT_DISCOUNT_FACTOR = 0.95;
	public static final double ERROR_THRESHOLD = 1E-30;
	
	private double discountFactor;
	
	private Agent myAgent;
	private BehaviorLogger logger;
	
	private List<State> states = new ArrayList<State>();
	private List<AgentAction> actions = new ArrayList<AgentAction>();
	
	private Map<State, Double> V = new HashMap<State, Double>();
	private Map<State, Map<AgentAction, Double>> R = new HashMap<State, Map<AgentAction, Double>>();
	private Map<State, Map<AgentAction, Double>> Q = new HashMap<State, Map<AgentAction, Double>>();
	private Map<State, Map<AgentAction, Map<State, Double>>> T = new HashMap<State, Map<AgentAction, Map<State, Double>>>();
	private Map<State, AgentAction> A = new HashMap<State, AgentAction>();
	
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
					
		this.discountFactor = agent.readProperty("discount-factor", Double.class, DEFAULT_DISCOUNT_FACTOR);
		
		this.myAgent = agent;
		this.logger = new BehaviorLogger();
		
		setupModel(topology, td);
		fillTables(td, topology, 0.05);
	}
	

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		
		State state = new State(vehicle, availableTask);
		AgentAction agentAction = A.get(state);
				
		Action action;
				
		if (agentAction.isPickup()) {
			action = new Pickup(availableTask);
		} else {
			action = new Move(agentAction.moveCity);
		}

		logger.logProfit(myAgent);
		
		return action;
	}	
	
	public double transitionProbability(State initialState, AgentAction action, State targetState, TaskDistribution td) {
		
		// Chooses the next city according to the current state and action selected
		//
		// - If the agent picks up the task, the next city is the destination of the task
		// - If the agent decides to move, the next city is contained in the move decision
		//
		City nextCity = action.isPickup() ? initialState.destinationCity : action.moveCity;
		
		
		// Check if the target state's current city is the same as where the agent is going.
		if (targetState.currentCity == nextCity) {
			
			// Returns probability in this city according to the task distribution parameter.
			return td.probability(targetState.currentCity, targetState.destinationCity);
		} else {
			
			// Zero chance to be in a different city than the destination if we pick up.
			return 0.0;
		}
	}
	
	private void setupModel(Topology topology, TaskDistribution td) {
		
		// Create all actions
		
		actions.add(new AgentAction());
		
		for (City initialCity : topology.cities()) {
			actions.add(new AgentAction(initialCity));
		}
		
		// Create all states
		
		for (City initialCity : topology.cities()) {
			
			states.add(new State(initialCity));
			
			for (City destinationCity : topology.cities()) {
				states.add(new State(initialCity, destinationCity));
			}
		}		
		
		// Fill all maps with default values
		
		for (State state: states) {
			
			R.put(state, new HashMap<AgentAction, Double>());
			Q.put(state, new HashMap<AgentAction, Double>());
			T.put(state, new HashMap<AgentAction, Map<State, Double>>());
			
			for (AgentAction action: actions) {
				T.get(state).put(action, new HashMap<State, Double>());
			}
		}
	}
	
	private double calculateReward(TaskDistribution td, State state, AgentAction action) {

		double reward = 0;
		Vehicle vehicle = myAgent.vehicles().get(0);
		
		if ((action.isPickup() && !state.hasTask()) || 
			(!action.isPickup() && !state.currentCity.hasNeighbor(action.moveCity))) {
			
			// Not possible, so we put the lowest weight.
			return Double.NEGATIVE_INFINITY;
		}
		
		City currentCity = state.currentCity;
		City destinationCity = action.isPickup() ? state.destinationCity : action.moveCity;
		
		if (action.isPickup()) {
			reward += td.reward(currentCity, destinationCity);
		}
		
		reward -= currentCity.distanceTo(destinationCity) * vehicle.costPerKm();
		
		return reward;
	}
	
	public void fillTables(TaskDistribution td, Topology topology, double epsilon) {
				
		for (State state: states) {
			
			V.put(state, 0.0);
			
			for (AgentAction action: actions) {
								
				R.get(state).put(action, calculateReward(td, state, action));
				
				for (State statePrime: states) {
					double t = transitionProbability(state, action, statePrime, td);
					T.get(state).get(action).put(statePrime, t);
				}
			}
		}
		
		float error;
		
		do {
			error = iterateQ();
		} while (error >= ERROR_THRESHOLD);
	}
	
	private float iterateQ() {
		
		float error = 0;
		
		for (State state: states) {
				
			for (AgentAction action: actions) {
				
				double r = R.get(state).get(action);
				double sum = 0;
				
				if (r == Double.NEGATIVE_INFINITY) {
					Q.get(state).put(action, Double.NEGATIVE_INFINITY);
					continue;
				}
				
				for (State statePrime: states) {
					
					double t = T.get(state).get(action).get(statePrime);
					double v = V.get(statePrime);
					
					sum += t * v;
				}
				
				sum *= this.discountFactor;
				sum += r;
								
				Q.get(state).put(action, sum);
			}
			
			// Find action that maximizes Q.
				
			double bestValue = Double.NEGATIVE_INFINITY;
			AgentAction bestAction = null;
			
			for (AgentAction action: actions) {
				
				double q = Q.get(state).get(action);
				
				if (q > bestValue) {
					bestValue = q;
					bestAction = action;
				}
			}
			
			// Update error according to least square policy
			error += Math.pow(V.get(state) - bestValue, 2);
			
			// Update A and V with best action and its value
			V.put(state, bestValue);
			A.put(state, bestAction);
		}
		
		return error;
	}
	
	class State {
		
		public City currentCity = null;
		public City destinationCity = null;
		
		public State(City currentCity) {
			this(currentCity, null);
		}
		
		public State(City currentCity, City destinationCity) {
			this.currentCity = currentCity;
			this.destinationCity = destinationCity;
		}
		
		public State(Vehicle vehicle, Task availableTask) {
			
			if (availableTask == null) {
				this.currentCity = vehicle.getCurrentCity();
			} else {
				this.currentCity = vehicle.getCurrentCity();
				this.destinationCity = availableTask.deliveryCity;
			}
		}
		
		public boolean hasTask() {
			return destinationCity != null;
		}
		
		@Override
	    public boolean equals(Object o) {

	        if (o == this) return true;
	        if (!(o instanceof State)) { return false; }
	        
	        State that = (State) o;
	        return Objects.equals(currentCity, that.currentCity) &&
	               Objects.equals(destinationCity, that.destinationCity);
	    }

	    @Override
	    public int hashCode() {
	        return Objects.hash(currentCity, destinationCity);
	    }
	    
	    @Override
	    public String toString() {
	    	
	    	if (hasTask()) {
	    		return "{ State | " + currentCity + ", task to " + destinationCity + " }";
	    	} else {
	    		return "{ State | " + currentCity + ", no task" + " }";
	    	}
	    }
	}
	
	class AgentAction {
		
		public City moveCity = null;
		
		public AgentAction() {
			this(null);
		}
		
		public AgentAction(City moveCity) {
			this.moveCity = moveCity;
		}
		
		public boolean isPickup() {
			return moveCity == null;
		}
		
		@Override
	    public boolean equals(Object o) {
	
		    if (o == this) return true;
		    if (!(o instanceof AgentAction)) { return false; }
		        
		    AgentAction that = (AgentAction) o;
		    return Objects.equals(moveCity, that.moveCity);
	    }

	    @Override
	    public int hashCode() {
	    	if (moveCity == null) {
	    		return -1;
	    	} else {
	    		return Objects.hash(moveCity);
	    	}
	    }
	    
	    @Override
	    public String toString() {
	    	
	    	if (isPickup()) {
	    		return "{ Action | Pick-up }";
	    	} else {
	    		return "{ Action | Move to + " + moveCity + " }";
	    	}
	    }
	}
}
