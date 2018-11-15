package company;

import logist.agent.Agent;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;

public class ProfitMarginCompany extends SmartCompany {

	int margin;
	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		super.setup(topology, distribution, agent);
		
		margin = agent.readProperty("margin", Integer.class, 0);	
	}
	
	@Override
	public Long askPrice(Task task) {
		return super.askPrice(task) + margin;
	}
}
