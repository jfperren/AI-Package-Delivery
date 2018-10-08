package reactive;

import logist.agent.Agent;

public class BehaviorLogger {
	
	private int numActions = 0;

	public final void logProfit(Agent agent) {
		
		if (numActions >= 1) {
			System.out.println(agent.name() + ": The total profit after "+numActions+" actions is "+agent.getTotalProfit()+" (average profit: "+(agent.getTotalProfit() / (double)numActions)+")");
		}
		
		numActions++;
	}	
}
