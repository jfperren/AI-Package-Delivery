package template;

import java.util.ArrayList;
import java.util.List;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;
import template.DeliberativeTemplate.Action;
import template.DeliberativeTemplate.Mode;
import template.DeliberativeTemplate.Node;

class State {
	
	public City currentCity;
	public TaskSet availableTasks;
	public TaskSet transportedTasks;
	public int capacity;
//	public int cost;
//	public State parent; 
	
	public State(
		City currentCity, 
		TaskSet availableTasks, 
		TaskSet transportedTasks, 
		int capacity
//		int cost,
//		State parent
	) {
		this.currentCity = currentCity;
		this.availableTasks = availableTasks;
		this.transportedTasks = transportedTasks;
		this.capacity = capacity;
//		this.cost = cost;
//		this.parent = parent;
	}
	
	public State(Vehicle vehicle, TaskSet tasks) {
		this.currentCity = vehicle.getCurrentCity();
		this.availableTasks = tasks;
		this.transportedTasks = vehicle.getCurrentTasks();
		this.capacity = vehicle.capacity();
	}
	
	public List<State> nextStates(int costPerKm) {
		
		List<State> nextStates = new ArrayList<State>();
		
		for (Task task: availableTasks) {
			
			if (task.deliveryCity == currentCity && task.weight >= capacity) {
				
				// Pick-up the task
				
				TaskSet newAvailableTasks = availableTasks.clone();
				newAvailableTasks.remove(task);
				
				TaskSet newTransportedTasks = transportedTasks.clone();
				newTransportedTasks.add(task);
				
				nextStates.add(new State(
					currentCity,
					newAvailableTasks,
					newTransportedTasks,
					capacity - task.weight
//					cost,
//					this
				));
			}
		}
		
		for (Task task: transportedTasks) {
			
			if (task.pickupCity == currentCity) {
				
				// Deliver the task
				
				TaskSet newAvailableTasks = availableTasks.clone();
				newAvailableTasks.remove(task);
				
				TaskSet newTransportedTasks = transportedTasks.clone();
				newTransportedTasks.add(task);
				
				nextStates.add(new State(
					currentCity,
					newAvailableTasks,
					newTransportedTasks,
					capacity + task.weight
//					cost,
//					this
				));
			}
		}

		for (City city: currentCity.neighbors()) {
			
			// Move to the city
			
			nextStates.add(new State(
				city,
				availableTasks,
				transportedTasks,
				capacity
//				cost + currentCity.distanceTo(city) * int(costPerKm),
//				this
			));
		}
		
		return nextStates;
	}
		
	
//		public Node afterMove(City city) {	
//			
//			path = new ArrayList<Action>();
//			path.addAll(this.path);
//			path.add(new Action(city));
//			
//			return new Node(
//				new State(
//					city,
//					availableTasks,
//					transportedTasks,
//					capacity
//				),
//				value + 0,
//				path
//			
//				
//				
//			);
//			
//			
//			return ;
//		}
//		
//		public State afterPickup(Task task) {	
//			
//			TaskSet newAvailableTasks = availableTasks.clone();
//			newAvailableTasks.remove(task);
//			
//			TaskSet newTransportedTasks = transportedTasks.clone();
//			newTransportedTasks.add(task);
//			
//			return new State(
//				currentCity,
//				newAvailableTasks,
//				newTransportedTasks,
//				capacity - task.weight
//			);
//		}
//		
//		public State afterDelivery(Task task) {	
//						
//			TaskSet newTransportedTasks = transportedTasks.clone();
//			newTransportedTasks.remove(task);
//			
//			return new State(
//				currentCity,
//				availableTasks,
//				newTransportedTasks,
//				capacity + task.weight
//			);
//		}
//		
//		public boolean isFinal() {
//			return availableTasks.isEmpty() && transportedTasks.isEmpty();
//		}
//	}
}