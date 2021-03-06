package org.princehouse.mica.lib;


import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import org.princehouse.mica.base.BaseProtocol;
import org.princehouse.mica.base.annotations.GossipUpdate;
import org.princehouse.mica.base.annotations.SelectUniformRandom;
import org.princehouse.mica.base.net.model.Address;


/**
 * TODO (unfinished)
 * 
 * @author lonnie
 *
 */
public class CTRWSamplerOverlay extends BaseProtocol {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public static class SamplingMessage {
		public Address source;
		public double timer;
		public void update(int degree) { 
			double u = new Random().nextDouble();
			timer -= -Math.log10(u) / (double) degree;
		}
		public boolean finished() {
			return timer <= 0;
		}
	}
	
	public Queue<SamplingMessage> messages = new LinkedList<SamplingMessage>();
	
	@SelectUniformRandom
	public Set<Address> select() {
		SamplingMessage m = messages.peek(); // next message in the queue
		if(m == null) 
			return null; // No messages.  Do not gossip.
		else if(m.finished()) {
			Set<Address> s = new HashSet<Address>();
			s.add(m.source);
			return s;
		} else {
			return view;
		}		
	}
	
	@SelectUniformRandom
	public Set<Address> view;
	
	public CTRWSamplerOverlay(Set<Address> view) {
		this.view = view;
	}
	
	@GossipUpdate
	public void exchange(CTRWSamplerOverlay other) {
		SamplingMessage m = messages.poll(); 
		if(m.finished()) {
			other.samplePeer(this);
		} else {
			// pass the message along to a neighbor			
			m.update(getDegree());
			other.messages.add(m);
		}
	}
	
	public void samplePeer(CTRWSamplerOverlay peer) {
		// TODO client application should do something with this 
	}
	
	public int getDegree() {
		return view.size();
	}
	
	

}
