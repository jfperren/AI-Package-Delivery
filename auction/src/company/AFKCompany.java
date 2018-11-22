package company;

import java.util.ArrayList;
import java.util.List;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;

public class AFKCompany extends AbstractCompany {

	@Override
	public Long askPrice(Task task) {
		return null;
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		
		List<Plan> plans = new ArrayList<Plan>();
		
		for (int i = 0; i < vehicles.size(); i++) {
			plans.add(Plan.EMPTY);
		}
		
		return plans;
	}
}
