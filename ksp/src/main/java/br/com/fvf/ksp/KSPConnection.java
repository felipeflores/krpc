package br.com.fvf.ksp;

import java.io.IOException;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;

public class KSPConnection {

	private Connection connection;
	
	public KSPConnection()  {
		Connection connection;
		try {
			connection = Connection.newInstance();
			KRPC krpc = KRPC.newInstance(connection);
			System.out.println("Connected to kRPC version " + krpc.getStatus().getVersion());
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (RPCException e) {
			throw new RuntimeException(e);
		}
	    
	}
	
	public Connection getConnection() {
		return connection;
	}
	
	public void close() {
		try {
			connection.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
}
