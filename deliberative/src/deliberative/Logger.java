package deliberative;

import java.util.Date;

import logist.plan.Plan;
import logist.simulation.Vehicle;

public class Logger {
	
	private int counter = 0;
	private String name = null;
	private long start = 0;

	public void initialize(String name) {
		this.counter = 0;
		this.name = name;
		this.start = new Date().getTime();
	}
	
	public void increment() {
		this.counter++;
	}
	
	public void logResults(Plan plan, Vehicle vehicle) {
		
		long time = new Date().getTime() - start;
		
		System.out.println(name + ": Found plan in " + counter + " steps (" + time + "ms). Plan has total distance " 
				+ plan.totalDistance() + " (total cost: " + plan.totalDistance() * vehicle.costPerKm() + " ).");
	}
	
	static class EmptyLogger extends Logger {

		@Override
		public void initialize(String name) { }
		
		@Override
		public void increment() { }
		
		@Override
		public void logResults(Plan plan, Vehicle vehicle) { }
	}
}
