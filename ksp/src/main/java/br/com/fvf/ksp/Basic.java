package br.com.fvf.ksp;

import java.io.IOException;

import krpc.client.Connection;
import krpc.client.services.SpaceCenter;

public class Basic {

	
	public static void main(String[] args) throws IOException {
		Connection connection = Connection.newInstance();
		SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
		
		
	}
}
