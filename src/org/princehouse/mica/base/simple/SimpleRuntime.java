package org.princehouse.mica.base.simple;


import java.io.IOException;
import java.net.ConnectException;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.princehouse.mica.base.BaseProtocol;
import org.princehouse.mica.base.model.Compiler;
import org.princehouse.mica.base.model.Protocol;
import org.princehouse.mica.base.model.Runtime;
import org.princehouse.mica.base.model.RuntimeAgent;
import org.princehouse.mica.base.model.RuntimeState;
import org.princehouse.mica.base.net.model.AcceptConnectionHandler;
import org.princehouse.mica.base.net.model.Address;
import org.princehouse.mica.base.net.model.Connection;
import org.princehouse.mica.util.Distribution;
import org.princehouse.mica.util.WeakHashSet;


/**
 * Basic Runtime implementation.
 * 
 * Nothing fancy: It just serializes and exchanges complete node state.
 * 
 */
public class SimpleRuntime<P extends Protocol> extends Runtime<P> implements
AcceptConnectionHandler {

	private ReentrantLock lock = new ReentrantLock();

	public static int DEFAULT_INTERVAL = 500;
	private static long LOCK_WAIT_MS = DEFAULT_INTERVAL;

	public static long DEFAULT_RANDOM_SEED = 0L;

	public Address address;

	protected SimpleRuntime(Address address) {
		super();
		this.address = address;
	}

	/**
	 * Entry point for SimpleRuntime.  Starts a protocol in a new thread. 
	 * 
	 * @param pinstance Local protocol instance
	 * @param address Local address
	 * @param daemon Launch thread as a daemon
	 * @return New Runtime instance
	 */
	public static <T extends Protocol> Runtime<T> launch(final T pinstance,
			final Address address, final boolean daemon) {
		final Runtime<T> rt = new SimpleRuntime<T>(address);
		Thread t = new Thread() {
			public void run() {
				try {
					rt.run(pinstance, address, DEFAULT_INTERVAL,
							DEFAULT_RANDOM_SEED);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};
		t.setDaemon(daemon);
		t.start();
		return rt;
	}

	/**
 	 * Entry point for SimpleRuntime.  Starts a protocol in a new thread. 
	 * (Calls through to launch(), with the daemon flag true)
	 * 
	 * @param pinstance Local protocol instance
	 * @param address Local address
	 * @return New Runtime instance
	 */
	public static <T extends Protocol> Runtime<T> launchDaemon(final T pinstance,
			final Address address) {
		return launch(pinstance,address,true);
	}
	
	private P pinstance;

	@Override
	public void acceptConnection(Address recipient, Connection connection)
			throws IOException {
		try {
			if (lock.tryLock(LOCK_WAIT_MS, TimeUnit.MILLISECONDS)) {
				setRuntime(this);
				((SimpleRuntimeAgent<P>) compile(pinstance)).acceptConnection(
						this, getProtocolInstance(), connection);
				clearRuntime(this);
				lock.unlock();
			} else {
				// failed to acquire lock; timeout
				//((BaseProtocol)pinstance).log("accept-lock-fail");
				System.err.printf("%s accept: failed to acquire lock with (timeout)\n", this);
				connection.close();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private boolean running = true;

	@Override
	public void run(P pinstance, Address address, int intervalMS,
			long randomSeed) throws InterruptedException {

		super.run(pinstance, address, intervalMS, randomSeed);

		// Initialize RuntimeState available to protocols
		runtimeState.setAddress(address);
		runtimeState.setRandom(new Random(randomSeed));

		setProtocolInstance(pinstance);

		try {
			address.bind(this);
		} catch (IOException e1) {
			punt(e1);
		}

		long lastElapsedMS = 0L;

		Random rng = new Random(randomSeed);

		((BaseProtocol) pinstance).logstate();
		
		pinstance.initialize();

		while (running) {
			double rate = getRate(getProtocolInstance());
			((BaseProtocol) getProtocolInstance()).log("rate,%g",rate);
			int intervalLength = (int) (((double) intervalMS) / rate);
			if(intervalLength <= 0) {
				System.err.printf("%s error: Rate * intervalMS <= 0.  Resetting to default.\n", this);
				intervalLength  = intervalMS;
			}
			Thread.sleep(Math.max(0L, intervalLength - lastElapsedMS));
			if (!running)
				break;

			long startTime = getTimeMS();

			Connection connection = null;
			Address partner = null;

			try {
				if (lock.tryLock(LOCK_WAIT_MS, TimeUnit.MILLISECONDS)) {
					partner = compile(getProtocolInstance()).select(this,
							getProtocolInstance(), rng.nextDouble());

					Runtime.debug.printf("%s select %s\n", this, partner);

					((BaseProtocol) getProtocolInstance()).log("select,%s", partner);

					if (partner == null) {
						lock.unlock();  // bugfix retroactively added to master 3/2/12
						continue;
					}

					if (!running) {
						lock.unlock();
						break;
					}

					connection = partner.openConnection();

//					System.out.println("Running preGossip on: "+getProtocolInstance());
					getProtocolInstance().preGossip(partner);

					compile(getProtocolInstance()).gossip(this, getProtocolInstance(), connection);

//					System.out.println("Running postGossip on: "+getProtocolInstance());
					getProtocolInstance().postGossip(partner);

					lock.unlock();
				} else {
					// failed to acquire lock within time limit; gossip again
					// next round
					Runtime.debug.printf(
							"%s active lock fail on init gossip [already engaged in gossip?]\n", this);
					((BaseProtocol) getProtocolInstance()).log("lockfail-active");
				}
			} catch (ConnectException e) {
				Runtime.debug.printf("%s unable to connect to %s\n", this, partner);
				lock.unlock();
			} catch (IOException e) {
				lock.unlock();
				e.printStackTrace();
			}
			lastElapsedMS = getTimeMS() - startTime;
		}
	}

	@Override
	public String toString() {
		return String.format("<rt %s>", getAddress());
	}

	private long getTimeMS() {
		return new Date().getTime();
	}

	@Override
	public void stop() {
		running = false;
	}

	@Override
	public P getProtocolInstance() {
		return pinstance;
	}

	@Override
	public void setProtocolInstance(P pinstance) {
		this.pinstance = pinstance;
	}

	@Override
	public Address getAddress() {
		return address;
	}

	// ---- agent execution context utilities ------------
	private WeakHashSet<Object> foreignObjects = null;
	private RuntimeState foreignState = null;

	protected void setForeignState(WeakHashSet<Object> foreignObjects,
			RuntimeState foreignState) {
		// only knows about foreign BaseProtocol subclasses for now
		this.foreignObjects = foreignObjects;
		this.foreignState = foreignState;
	}

	protected void clearForeignState() {
		foreignObjects = null;
		foreignState = null;
	}

	// ---------------------------------------------------

	private RuntimeState runtimeState = new RuntimeState();

	@Override
	public RuntimeState getRuntimeState(Protocol p) {
		if (foreignObjects != null) {
			if (foreignObjects.contains(p))
				return foreignState;
		}
		return runtimeState;
	}

	@Override
	public RuntimeState getRuntimeState() {
		return runtimeState;
	}

	@Override
	public Distribution<Address> getSelectDistribution(
			Protocol protocol) {
		Distribution<Address> dist = compile(protocol).getSelectDistribution(this, protocol);
		if(dist == null)
			dist = new Distribution<Address>(); // empty

		return dist;
	}

	@Override
	public double getRate(Protocol protocol) {
		return compile(protocol).getRate(this,protocol);
	}

	@Override
	public void executeUpdate(Protocol p1, Protocol p2) {
		// TODO sanity check that p1, p2 are same types, or have a common parent
		// class
		SimpleRuntimeAgent<Protocol> agent = (SimpleRuntimeAgent<Protocol>) compile(p1);
		agent.executeUpdate(this, p1, p2);
	}

	private Compiler compiler = new SimpleCompiler();

	@Override
	public <T extends Protocol> RuntimeAgent<T> compile(T pinstance) {
		return compiler.compile(pinstance);
	}

}