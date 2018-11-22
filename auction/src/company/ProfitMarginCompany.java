package company;


import logist.agent.Agent;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;

public class ProfitMarginCompany extends SmartCompany {

	public static final double DEFAULT_MARGIN = 1.0;
	public static final double DEFAULT_LEARNING_RATE = 0.6;
	
	protected double margin;
	protected double learningRate;
	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		super.setup(topology, distribution, agent);
		
		margin = agent.readProperty("margin", Double.class, DEFAULT_MARGIN);
		learningRate = agent.readProperty("learningRate", Double.class, DEFAULT_LEARNING_RATE);
		
		log = true;
	}
	
	@Override
	public Long askPrice(Task task) {
		
		if (!canCarry(task)) {
			return null;
		}
		
		logMessage("Current Margin: " + margin);
		double bid = discount(task) * super.askPrice(task) * margin;
		
		bid = Math.max(25.0, bid);
		
		return (long) bid;
	}
	
	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		
		super.auctionResult(previous, winner, bids);
				
		if (id == winner) {
			
			margin = margin - 0.05;
			
		} else {
			margin = margin + 0.05;
		}
		
//		margin = (1 - learningRate) * margin + learningRate * (bids[1 - id] / (double) bids[id]);
		
//		if (winner == id) {
//			logMessage("Increasing margin by: " + (1 + learningRate));
//			margin = margin * (1 + learningRate);
//		} else {
//			logMessage("Decreasing margin by: " + (1 - learningRate));
//			margin = margin * (1 - learningRate);
//		}
	}
}
