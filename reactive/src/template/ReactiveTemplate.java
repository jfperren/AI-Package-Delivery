package template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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


public class ReactiveTemplate implements ReactiveBehavior {

	class StateList {
		
		private List<State> states; 
		
		public StateList(Topology topology) {
			
			states = new ArrayList<State>();
			
			for (City initialCity : topology.cities()) {
				states.add(new State(initialCity));
			}
			
			for (City initialCity : topology.cities()) {
				for (City destinationCity : topology.cities()) {
					states.add(new State(initialCity, destinationCity));
				}
			}		
		}
		
		public List<State> states() {
			return states;
		}
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
		
		public boolean hasTask() {
			return destinationCity != null;
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
	}
	
	class AgentActionList {
		
		private List<AgentAction> actions; 
		
		public AgentActionList(Topology topology) {
			
			actions = new ArrayList<AgentAction>();
			
			actions.add(new AgentAction());
			
			for (City initialCity : topology.cities()) {
				actions.add(new AgentAction(initialCity));
			}
		}
		
		public List<AgentAction> actions() {
			return actions;
		}
	}
		
	class Decision {
		public final State state = null;
		public final Action action = null;
	}
	
	class Transition {
		public final State initialState = null;
		public final State finalState = null;
		public final Action action = null;
	}
	
	private Random random;
	private double pPickup;
	private Agent myAgent;
	private int numActions;
	
	
//	private Map<State, Double> V = new HashMap<State, Double>();
	private Map<Decision, Double> R = new HashMap<Decision, Double>();
	private Map<Transition, Double> T = new HashMap<Transition, Double>();

	private List<Double> V = new ArrayList<Double>();
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);

		this.random = new Random();
		this.pPickup = discount;
		this.numActions = 0;
		this.myAgent = agent;
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
		
		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
	
	public double initialValue(State initialState) {
		return 0.0;
	}
	
	public double transitionProbability(State initialState, State targetState, AgentAction action, TaskDistribution td) {
		
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
}
