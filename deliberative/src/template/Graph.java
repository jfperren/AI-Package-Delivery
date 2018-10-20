package template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import logist.topology.Topology.City;


public class Graph<Node> {
	
	public Set<Node> nodes;
	public Set<Edge<Node>> edges;
	
	public Graph(Set<Node> nodes, Set<Edge<Node>> edges) {
		
		this.nodes = nodes;
		this.edges = edges;
		
	}
	
	private Node root(Node child, Map<Node, Node> parents) {
		
		Node node = child;
		
		while (parents.get(node) != null) {
			node = parents.get(node);
		} 
		
		return node;
	}
	
	public double mstWeight() {
		
		PriorityQueue<Edge<Node>> edgesToVisit = new PriorityQueue<Edge<Node>>(edges);
		Map<Node, Node> parents = new HashMap<Node, Node>();
		
		double totalWeight = 0;

		while (!edgesToVisit.isEmpty()) {
			
			Edge<Node> edge = edgesToVisit.poll();
						
			Node rootA = root(edge.a, parents);
			Node rootB = root(edge.b, parents);
			
			if (rootA != rootB) {
				totalWeight += edge.weight;
				parents.put(rootA, rootB);
			}
		}
		
		return totalWeight;
	}
	
	public static Graph<City> completeCityGraph(Set<City> cities) {
		
		Set<Edge<City>> edges = new HashSet<Edge<City>>();
		List<City> cityArray = new ArrayList<City>(cities);
		
		for (int i = 0 ; i < cityArray.size() - 1; i++) {
			for (int j = i+1; j < cityArray.size(); j++) {
				
				City city = cityArray.get(i);
				City otherCity = cityArray.get(j);
				
				double dx = otherCity.xPos - city.xPos;
				double dy = otherCity.yPos - city.yPos;
				double distance = Math.sqrt(dx * dx + dy *  dy);
				
				edges.add(new Edge<City>(city, otherCity, distance));
			}
		}
		
		return new Graph<City>(cities, edges);
	}
	
	static class Edge<Node> implements Comparable<Edge<Node>> {
		
		public Node a = null;
		public Node b = null;
		public Double weight = 0.0;
		
		public Edge(Node a, Node b, Double weight) {
			this.a = a;
			this.b = b;
			this.weight = weight;
		}
		
		@Override
		public int compareTo(Edge<Node> that) {
			return weight < that.weight ? -1 : 1;
		}
		
		@Override
		public String toString() {
			return a + "-" + b + "@" + weight;
		}
	}
	
	static class DisjointSet<E> {
		
		private Map<E, E> parents = new HashMap<E, E>();
		
		
		
		public E root(E elem) {
			return rootAndDepth(elem).x;
			
		}
		
		public int depth(E elem) {
			return rootAndDepth(elem).y;
		}
		
		private Tuple<E, Integer> rootAndDepth(E elem) {
			
			E root = elem;
			int depth = 0;
			
			while (parents.get(root) != null) {
				root = parents.get(root);
				depth++;
			} 
			
			return new Tuple<E, Integer>(root, depth);
		}
		
		public boolean connected(E lhs, E rhs) {
			return root(lhs) == root(rhs);
		}
		
		public void connect(E rhs, E lhs) {
			parents.put(rhs, lhs);
		}
	}
}
