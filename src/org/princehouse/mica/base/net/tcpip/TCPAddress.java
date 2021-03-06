package org.princehouse.mica.base.net.tcpip;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import org.princehouse.mica.base.net.BaseConnection;
import org.princehouse.mica.base.net.model.AcceptConnectionHandler;
import org.princehouse.mica.base.net.model.Address;
import org.princehouse.mica.base.net.model.Connection;


/**
 * TCP/IP Address implementation.
 * 
 * See TCPAddress.valueOf to create a TCPAddress from a "host:port" string
 * 
 * @author lonnie
 *
 */
public class TCPAddress implements Address, Externalizable {
	
	/**
	 * Used when interpreting an address from a String if no port is specified
	 */
	public static final int DEFAULT_PORT = 8000; 
	
	/** 
	 * Convert a "host:port" string into a TCPIPAddress instance
	 * 
	 * @param addr
	 * @return
	 * @throws UnknownHostException
	 */
	public static TCPAddress valueOf(String addr) throws UnknownHostException {
		// TODO Auto-generated method stub
		int port;
		String host;
		
		if(addr.indexOf(':') >= 0) {			
			port = Integer.valueOf(addr.substring(addr.indexOf(':')+1)); 
			host = addr.substring(0,addr.indexOf(':'));
		} else {
			port = DEFAULT_PORT;
			host = addr;
		}
		return new TCPAddress(InetAddress.getByName(host), port);
	}
	
	private AcceptConnectionHandler receiveCallback;
	protected InetAddress address;
	protected ServerSocket sock;
	
	int port;
	
	/**
	 * Default constructor
	 */
	public TCPAddress() {}
	
	/**
	 * Constructor from IP address and port number
	 *  
	 * @param address
	 * @param port
	 */
	public TCPAddress(InetAddress address, int port) {
		this.address = address;
		this.port = port;
	}
	
	/**
	 * A copy constructor that create a deep copy of the given address. Added
	 *
	 * @author Josh Endries (jce54@cornell.edu)
	 * @param a The address to copy.
	 */
	public TCPAddress(TCPAddress a) {
		try {
			this.address = InetAddress.getByName(a.getInetAddressAddress().getHostAddress());
			this.port = a.getPort();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	protected void acceptCallback(Socket clientSocket) {
		// clientSocket is returned from ServerSocket.accept
		assert(receiveCallback != null);
		
		try {
			Connection c = new SocketConnection(clientSocket);
			receiveCallback.acceptConnection(this,c);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void bind(AcceptConnectionHandler h) {
		receiveCallback = h;
		int backlog = 10;
		try {
			sock = new ServerSocket(port, backlog, address);
			sock.setReuseAddress(true);
		} catch(java.net.BindException e) {
			System.err.printf("In use: %s port %s\n",address, port);
			throw new RuntimeException(e);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("socket binding failed");
		}
		AsyncServer.getServer().bind(this);
	}

	@Override
	public int compareTo(Address o) {
		return toString().compareTo(o.toString());
	}
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof TCPAddress)) {
			return false;
		} else { 
			TCPAddress t = (TCPAddress) o;
			return address.equals(t.address) && port == t.port;
		}
		
	}
	
	/**
	 * Get the IP address as a Java InetAddress
	 * @return An InetAddress that can be used with Java's built-in networking
	 */
	public InetAddress getInetAddressAddress() {
		return address;
	}

	/**
	 * Return the port associated with the address
	 * @return
	 */
	public int getPort() {
		return port;
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}
	
	@Override
	public Connection openConnection() throws IOException {
		// TODO Auto-generated method stub
		Socket sock = new Socket(address, port);
		BaseConnection c = new SocketConnection(sock);
		return c;
	}
	
	@Override
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		address = (InetAddress) in.readObject();
		port = (Integer) in.readObject();
	}
	
	public void setPort(int p) {
		port = p;
	}

	@Override
	public String toString() {
		return String.format("%s:%d",address,port);
	}

	@Override
	public void unbind() {
		// TODO not implemented
	}
	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(address);
		out.writeObject((Integer)port);
	}
}
