package template;

import logist.plan.Plan;

public class Logger {
	
	private int counter = 0;
	private String name = null;

	public void initialize(String name) {
		this.counter = 0;
		this.name = name;
	}
	
	public void increment() {
		this.counter++;
	}
	
	public void logResults(Plan plan) {
		System.out.println(name + ": Found plan in " + counter + " steps. Plan has total distance " 
				+ plan.totalDistance() + " and total units " + plan.totalDistanceUnits() + ".");
	}
	
	static class EmptyLogger extends Logger {

		@Override
		public void initialize(String name) { }
		
		@Override
		public void increment() { }
		
		@Override
		public void logResults(Plan plan) { }
	}
}
