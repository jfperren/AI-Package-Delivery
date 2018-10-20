package template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import logist.plan.Action;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;


class State {
	
	public City currentCity;
	public TaskSet availableTasks;
	public TaskSet transportedTasks;
	public int capacity;
	
	public State(
		City currentCity, 
		TaskSet availableTasks, 
		TaskSet transportedTasks, 
		int capacity
	) {
		this.currentCity = currentCity;
		this.availableTasks = availableTasks;
		this.transportedTasks = transportedTasks;
		this.capacity = capacity;
	}
	
	public State(Vehicle vehicle, TaskSet tasks) {
		this.currentCity = vehicle.getCurrentCity();
		this.availableTasks = tasks;
		this.transportedTasks = vehicle.getCurrentTasks();
		this.capacity = vehicle.capacity();
	}
	
	public List<Tuple<State, Action>> nextStates() {
		
		List<Tuple<State, Action>> nextStates = new ArrayList<Tuple<State, Action>>();
		
		for (Task task: availableTasks) {
			
			if (task.pickupCity == currentCity && task.weight <= capacity) {
				
				// Pick-up the task
				
				TaskSet newAvailableTasks = availableTasks.clone();
				newAvailableTasks.remove(task);
				
				TaskSet newTransportedTasks = transportedTasks.clone();
				newTransportedTasks.add(task);
				
				nextStates.add(new Tuple<State, Action>(new State(
					currentCity,
					newAvailableTasks,
					newTransportedTasks,
					capacity - task.weight
				), new Action.Pickup(task)));
			}
		}
		
		for (Task task: transportedTasks) {
			
			if (task.deliveryCity == currentCity) {
				
				// Deliver the task
				
				TaskSet newTransportedTasks = transportedTasks.clone();
				newTransportedTasks.remove(task);
				
				nextStates.add(new Tuple<State, Action>(new State(
					currentCity,
					availableTasks,
					newTransportedTasks,
					capacity + task.weight
				), new Action.Delivery(task)));
			}
		}

		for (City city: currentCity.neighbors()) {
			
			// Move to the city
			
			nextStates.add(new Tuple<State, Action>(new State(
				city,
				availableTasks,
				transportedTasks,
				capacity
			), new Action.Move(city)));
		}
		
		return nextStates;
	}
	
	public boolean isFinal() {
		return availableTasks.isEmpty() && transportedTasks.isEmpty();
	}
	
	public Double heuristic(Vehicle vehicle) {
		
		Set<City> cities = new HashSet<City>();
		
		for (Task task: availableTasks) {
			cities.add(task.deliveryCity);
			cities.add(task.pickupCity);
		}
		
		for (Task task: transportedTasks) {
			cities.add(task.deliveryCity);
		}
		
		cities.add(currentCity);
		
		return Graph.completeCityGraph(cities).mstWeight() * vehicle.costPerKm() ;
	}
	
	@Override
	public String toString() {
		return "At " + currentCity + " with tasks [" + transportedTasks + " and capacity " + capacity + ". Leftover tasks are " + availableTasks; 
	}
	
	@Override
    public boolean equals(Object o) {

        if (o == this) return true;
        if (!(o instanceof State)) { return false; }
        
        State that = (State) o;
        return Objects.equals(currentCity, that.currentCity) &&
               Objects.equals(capacity, that.capacity) &&
               Objects.equals(availableTasks, that.availableTasks) &&
               Objects.equals(transportedTasks, that.transportedTasks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentCity, capacity, availableTasks, transportedTasks);
    }
}
