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


/**
 * Agent Behavior class that implements a basic Reinforcement Learning Algorithm.
 *  
 * @author Julien Perrenoud & Pierre-Antoine Desplaces
 * 
 */
public class RLABehavior implements ReactiveBehavior {
		
	public static final double DEFAULT_DISCOUNT_FACTOR = 0.95;
	public static final double DEFAULT_DISCOUNT_MAX = 0.99;
	
	/** Error below which we consider two Q-tables to be "good enough" (Using LSE) */
	public static final double ERROR_THRESHOLD = 1E-30;
	
	/** Constant by which future rewards are multiplied. Should be in [0, 1). */
	private double discountFactor;
	
	private Agent myAgent;
	private BehaviorLogger logger;
	
	/** List of all possible states in the system, use it to simplify iteration */
	private List<State> states = new ArrayList<State>();
	
	/** List of all possible action in the system, use it to simplify iteration */
	private List<AgentAction> actions = new ArrayList<AgentAction>();
	
	/** 
	 *  Transition probabilities between two states using a specified action. Should be accessed
	 *  as T.get(initialState).get(action).get(targetState)
	 */
	private Map<State, Map<AgentAction, Map<State, Double>>> T = new HashMap<State, Map<AgentAction, Map<State, Double>>>();
	
	/** Infinite-horizon expected value of each state */
	private Map<State, Double> V = new HashMap<State, Double>();
	
	/** Immediate reward for undertaking an action in a certain state */
	private Map<State, Map<AgentAction, Double>> R = new HashMap<State, Map<AgentAction, Double>>();
	
	/** Q-Table used for offline RLA algorithm */
	private Map<State, Map<AgentAction, Double>> Q = new HashMap<State, Map<AgentAction, Double>>();
	
	/** Contain best action for each state after offline RLA is performed. */
	private Map<State, AgentAction> A = new HashMap<State, AgentAction>();
	
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
					
		this.discountFactor = agent.readProperty("discount-factor", Double.class, DEFAULT_DISCOUNT_FACTOR);
		this.discountFactor = Math.min(Math.max(discountFactor, 0.0), DEFAULT_DISCOUNT_MAX);
		
		this.myAgent = agent;
		this.logger = new BehaviorLogger();
		
		// 1. Initialize states and actions
		
		setupModel(topology);
		
		// 2. Create all tables and set their default values
		
		setupTables(topology, td);
		
		// 3. Perform Reinforcement Learning Algorithm until error is small enough
		
		double error;
		
		do {
			error = iterateQ();
		} while (error >= ERROR_THRESHOLD);
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
	
	/**
	 * Create list of all actions and states according to the topology of
	 * the current simulation.
	 * 
	 * @param topology topology of the network (to create states and actions)
	 * 
	 */
	private void setupModel(Topology topology) {
		
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
		
		
	}
	
	/**
	 * Initialize all tables required for RLA:
	 * 
	 *  - R is filled using "calculateReward"
	 *  - T is filled using "transitionProbability"
	 *  - Q is only initialized, there is no default value
	 *  - V is initialized to 0.0 as the value can be arbitrary.
	 * 
	 * @param topology topology of the network (to calculate rewards)
	 * @param td task distribution of the network (to calculate rewards and probabilities)
	 */
	public void setupTables(Topology topology, TaskDistribution td) {
		
		for (State state: states) {
			
			R.put(state, new HashMap<AgentAction, Double>());
			Q.put(state, new HashMap<AgentAction, Double>());
			T.put(state, new HashMap<AgentAction, Map<State, Double>>());
			V.put(state, 0.0);
			
			for (AgentAction action: actions) {
				
				T.get(state).put(action, new HashMap<State, Double>());
				R.get(state).put(action, calculateReward(td, state, action));
				
				for (State statePrime: states) {
					double t = transitionProbability(state, action, statePrime, td);
					T.get(state).get(action).put(statePrime, t);
				}
			}
		}
	}
	
	/**
	 * Calculate the transition probability from initialState to targetState when the
	 * agent undertakes a specified action. 
	 * 
	 * @param initialState the current state of the agent
	 * @param action the action taken by the agent
	 * @param targetState the state that agent will be in during the next step
	 * @param td the task distribution of the network
	 * 
	 * @return the probability of transition
	 */
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
	
	/**
	 * Calculate the reward associated with a given action in a given state. If the action
	 * is not possible according to the model (for instance, picking up a non-existent task
	 * or moving to a city that is not a direct neighbor), then it returns NEGATIVE_INFINITY.
	 * 
	 * @param td the task distribution of the network
	 * @param state the current state of the agent
	 * @param action the action taken by the agent
	 * @return the reward associated with the action, or NEGATIVE_INFINITY if the action is
	 * 		   not allowed.
	 */
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
	
	/**
	 * This represents one iteration of the RLA algorithm. It iterates over
	 * all possible states and actions, and updates the Q-table according to
	 * the Q-learning rule. Finally, it updates the A and V tables to reflect
	 * the best value and best action to undertake in each state.
	 * 
	 * @return the error between the previous values in the Q-table and the 
	 * updated ones, using the Least Square Error function.
	 */
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
				
				sum *= discountFactor;
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
	
	// Inner Classes
	
	/**
	 * Representation of a state in the model. If destinationCity is
	 * null, then currentCity does not contain a task to be delivered.
	 * (Note - This can also be checked using hasTask()).
	 */
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
	
	/**
	 * Representation of an action in the model. If moveCity is
	 * null, then it is a "pick-up" action. Otherwise, it is a
	 * "move" action. (Note - This can also be checked using 
	 * isPickup()).
	 */
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
