package template;

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


public class SmartBehavior implements ReactiveBehavior {
		
	public static final double DEFAULT_DISCOUNT_FACTOR = 0.95;
	public static final double DEFAULT_REWARD_FACTOR = 1;
	public static final double DEFAULT_DISTANCE_FACTOR = 1;
	public static final double ERROR_THRESHOLD = 1E-10;
	
	private double discountFactor;
	private double distanceFactor;
	private double rewardFactor;
	
	private Agent myAgent;
	private int numActions;
	
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
		this.distanceFactor = agent.readProperty("distance-factor", Double.class, DEFAULT_DISTANCE_FACTOR);
		this.rewardFactor = agent.readProperty("reward-factor", Double.class, DEFAULT_REWARD_FACTOR);
		
		this.numActions = 0;
		this.myAgent = agent;
		
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

		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
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
		}
		
		for (City initialCity : topology.cities()) {
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
			reward += rewardFactor * td.reward(currentCity, destinationCity);
		}
		
		reward -= distanceFactor * currentCity.distanceTo(destinationCity) * vehicle.costPerKm();
		
		return reward;
	}
	
	public void fillTables(TaskDistribution td, Topology topology, double epsilon) {
				
		for (State state: states) {
			
			V.put(state, (double) td.reward(state.currentCity, state.destinationCity));
			
			for (AgentAction action: actions) {
								
				R.get(state).put(action, calculateReward(td, state, action));
				
				for (State statePrime: states) {
					double t = transitionProbability(state, action, statePrime, td);
					T.get(state).get(action).put(statePrime, t);
				}
			}
		}
		
		float error = Float.POSITIVE_INFINITY;
		
		while (error >= ERROR_THRESHOLD) {
			error = iterateQ();
		}
	}
	
	private float iterateQ() {
		
		float error = 0;
		
		for (State state: states) {
				
			for (AgentAction action: actions) {
				
				double r = R.get(state).get(action);
				double sum = 0;
				
				for (State statePrime: states) {
					
					double t = this.T.get(state).get(action).get(statePrime);
					double v = this.V.get(statePrime);
					
					sum += t * v;
				}
				
				sum *= this.discountFactor;
				sum += r;
				
				// We add the square value to the total error
				if (Q.get(state).containsKey(action)) {
					error += Math.pow(Q.get(state).get(action) - sum, 2);
				} else {
					error = Float.POSITIVE_INFINITY;
				}
				
				Q.get(state).put(action, sum);
			}
			
			double maxValue = Double.NEGATIVE_INFINITY;
			AgentAction maxAction = null;
			
			for (AgentAction action: actions) {
				
				double q = Q.get(state).get(action);
				
				if (q > maxValue) {
					
					maxValue = q;
					maxAction = action;
					
					V.put(state, maxValue);
					A.put(state, maxAction);
				}
			}
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
