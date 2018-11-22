package company;

import java.util.ArrayList;
import java.util.List;

import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.simulation.VehicleImpl;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;

public class AdversarialCompany extends SmartCompany {


	public class Adversary extends SmartCompany {
		
		public void setup(Topology topology, TaskDistribution distribution, 
				int id, List<Vehicle> vehicles, double timeoutBid) {
			
			this.id = id;
			this.topology = topology;
			this.distribution = distribution;
			
			Vehicle lowestCostVehicle = null;
			double lowestCost = Double.POSITIVE_INFINITY;
			
			for (Vehicle vehicle: vehicles) {
				
				if (vehicle.costPerKm() < lowestCost) {
					lowestCostVehicle = vehicle;
					lowestCost = vehicle.costPerKm();
				}
			}
			
			for (int i = 0; i < topology.cities().size(); i++) {
				
				Vehicle newVehicle = new VehicleImpl(
					i,
					lowestCostVehicle.name(),
					lowestCostVehicle.capacity(),
					lowestCostVehicle.costPerKm(),
					topology.cities().get(i),
					(long) lowestCostVehicle.speed(),
					lowestCostVehicle.color()
				).getInfo();
				
				this.vehicles.add(newVehicle);
			}	
			
			this.log = false;
			this.timeoutBid = timeoutBid;
		}
	}
	
	private Adversary adversary;

	List<Long> myBids = new ArrayList<Long>();
	List<Long> theirBids = new ArrayList<Long>();
	List<Double> myCosts = new ArrayList<Double>();
	List<Double> theirCosts = new ArrayList<Double>();
	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		super.setup(topology, distribution, agent);
		
		log = true;		
		timeoutBid = timeoutBid / 2;
				
		adversary = new Adversary();
		adversary.setup(topology, distribution, 1 - agent.id(), vehicles, timeoutBid);
	}
	
	@Override
	public Long askPrice(Task task) {
		
		if (!canCarry(task)) {
			return null;
		}
		
		double myMarginalCost = marginalCost(task);
		double theirMarginalCost = adversary.marginalCost(task);
		
		logMessage("My Marginal cost (marginal): " + myMarginalCost);
		logMessage("Their Marginal cost: " + theirMarginalCost);
		
		theirCosts.add(theirMarginalCost);
		myCosts.add(myMarginalCost);
		
		Long bid = (long)((myMarginalCost + theirMarginalCost) * discount(task) / 2);
		
		if (bid <= 0) {
			
			Long theirLowestBid = Long.MAX_VALUE;
			
			for (Long theirBid: theirBids) {
				if (theirBid < theirLowestBid) { theirLowestBid = theirBid; }
			}
			
			bid = theirLowestBid - 2;
		}
		
		if (bid < 0.8 * discount(task) * myMarginalCost) {
			bid = (long) (0.8 * discount(task) * myMarginalCost);
		}
			
		return bid;
	}
	
	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		
		super.auctionResult(previous, winner, bids);
		adversary.auctionResult(previous, winner, bids);
		
		myBids.add(bids[id]);
		theirBids.add(bids[1 - id]);
	}
}