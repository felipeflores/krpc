package br.com.fvf.ksp;

import java.io.IOException;

import krpc.client.Connection;
import krpc.client.Event;
import krpc.client.RPCException;
import krpc.client.StreamException;
import krpc.client.services.KRPC;
import krpc.client.services.KRPC.Expression;
import krpc.client.services.SpaceCenter;
import krpc.client.services.SpaceCenter.Flight;
import krpc.client.services.SpaceCenter.ReferenceFrame;
import krpc.client.services.SpaceCenter.Vessel;
import krpc.schema.KRPC.ProcedureCall;

public class Comunicacao {

	private static Double ALTITUDE_10 = 500d;
	private static Double ALTITUDE_45 = 12000d;
	private static Double ALTITUDE_FIM_ATMOSFERA = 23000d;
	private static Double ALTITUDE_ORBITA = 80000d;
	private static Double VELOCIDADE_350 = 350d;
	
	private static boolean continua = true;

	public static void main(String[] args) throws IOException, RPCException, InterruptedException, StreamException {
		Connection connection = Connection.newInstance("Lancamento Orbita");
		KRPC krpc = KRPC.newInstance(connection);
		SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
		SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();

		vessel.getAutoPilot().targetPitchAndHeading(90, 90);
		vessel.getAutoPilot().engage();
		vessel.getControl().setThrottle(1);
		Thread.sleep(1000);

		// Countdown...
		System.out.println("3...");
		Thread.sleep(1000);
		System.out.println("2...");
		Thread.sleep(1000);
		System.out.println("1...");
		Thread.sleep(1000);
		System.out.println("Launch!");

		vessel.getControl().activateNextStage();
		Thread.sleep(1000);
		System.out.println("Rotacionando para 90");
		vessel.getAutoPilot().targetPitchAndHeading(90, 90);

		verificaAltitude(connection, krpc, vessel, ALTITUDE_10);
		System.out.println("Altitude chegou a " + ALTITUDE_10 + "m, inclinando 10º");

		float inclinacao = 80;
		vessel.getAutoPilot().targetPitchAndHeading(inclinacao, 90);

		verificandoVelocidade(connection, vessel, krpc);

		verificaApoapsis(connection, vessel, krpc);

		verificaAltitude(connection, krpc, vessel, ALTITUDE_45);

		System.out.println("Altitude chegou a " + ALTITUDE_45 + "m, inclinando 45º");

		while (inclinacao > 45) {
			inclinacao--;
			vessel.getAutoPilot().targetPitchAndHeading(inclinacao, 90);
			System.out.println("Inclinando para: " + inclinacao);
			Thread.sleep(1000);
		}

		
		verificaAltitude(connection, krpc, vessel, ALTITUDE_ORBITA);
		System.out.println("altitude de orbita, liga os motores para circularizar");
		vessel.getControl().setThrottle(1);
		
		
		{
			ProcedureCall apoapsisAltitude = connection.getCall(vessel.getOrbit(), "getPeriapsisAltitude");
			Expression expr = Expression.greaterThanOrEqual(connection, Expression.call(connection, apoapsisAltitude),
					Expression.constantDouble(connection, ALTITUDE_ORBITA));
			Event event = krpc.addEvent(expr);
			synchronized (event.getCondition()) {
				event.waitFor();
			}
		}
		System.out.println("o periapsis chegou a altitude orbita, desliga os motores");
		vessel.getControl().setThrottle(0);
	}

	private static void verificaApoapsis(final Connection connection, final Vessel vessel, final KRPC krpc) {
		Thread t = new Thread() {
			@Override
			public void run() {
				{
					try {
						ProcedureCall apoapsisAltitude = connection.getCall(vessel.getOrbit(), "getApoapsisAltitude");
						Expression expr = Expression.greaterThanOrEqual(connection, Expression.call(connection, apoapsisAltitude),
								Expression.constantDouble(connection, ALTITUDE_ORBITA));
						Event event = krpc.addEvent(expr);
						synchronized (event.getCondition()) {
							event.waitFor();
						}
						continua = false;
						System.out.println("chegou no apoapsis " + vessel.getOrbit().getApoapsisAltitude());
						vessel.getControl().setThrottle(0);
						Thread.sleep(2000);
						vessel.getAutoPilot().targetPitchAndHeading(0, 90);
						System.out.println("Inclinando para: " + 0);
					} catch (RPCException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (StreamException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		};
		t.start();

	}

	private static void verificaAltitude(Connection connection, KRPC krpc, SpaceCenter.Vessel vessel,
			Double altitudeComparacao) throws RPCException, StreamException {
		{
			ProcedureCall meanAltitude = connection.getCall(vessel.flight(null), "getMeanAltitude");
			Expression expr = Expression.greaterThan(connection, Expression.call(connection, meanAltitude),
					Expression.constantDouble(connection, altitudeComparacao));
			Event event = krpc.addEvent(expr);
			synchronized (event.getCondition()) {
				event.waitFor();
			}
		}
	}

	private static void verificandoVelocidade(final Connection connection, final SpaceCenter.Vessel vessel, KRPC krpc)
			throws RPCException, StreamException {
		
		final double velocidadeInicialTeste = 200d;
		
		ReferenceFrame srfFrame = vessel.getOrbit().getBody().getReferenceFrame();
		final Flight flight = vessel.flight(srfFrame);

		{
			ProcedureCall meanAltitude = connection.getCall(flight, "getSpeed");
			Expression expr = Expression.greaterThan(connection, Expression.call(connection, meanAltitude),
					Expression.constantDouble(connection, velocidadeInicialTeste));
			Event event = krpc.addEvent(expr);
			synchronized (event.getCondition()) {
				event.waitFor();
			}
		}
		// try {
		Thread t = new Thread() {

			@Override
			public void run() {
				while (continua) {
					// while (!Thread.currentThread().isInterrupted()) {
					try {

						double velocidade = flight.getSpeed();

						float throttleAtual = vessel.getControl().getThrottle();
						float thrustDiferenca = 0.05f;
						if (velocidade > velocidadeInicialTeste && velocidade < 300) {
							float thrustDiferencaBaixa = 0.01f;
							System.out.println("Velocidade(" + velocidade + ") > " + velocidadeInicialTeste + " reduzindo aos poucos "
									+ thrustDiferencaBaixa + " de Throttle");
							vessel.getControl().setThrottle(throttleAtual - thrustDiferencaBaixa);
						} else if (velocidade >= VELOCIDADE_350) {
							System.out.println("Velocidade(" + velocidade + ") > " + VELOCIDADE_350 + " reduzindo "
									+ thrustDiferenca + " de Throttle");
							vessel.getControl().setThrottle(throttleAtual - thrustDiferenca);
						} else if (velocidade < VELOCIDADE_350) {
							System.out.println("Velocidade(" + velocidade + ") < " + VELOCIDADE_350 + " aumentando "
									+ thrustDiferenca + " de Throttle");
							vessel.getControl().setThrottle(throttleAtual + thrustDiferenca);
						}

						double altitude = vessel.flight(null).getMeanAltitude();
						if (altitude >= ALTITUDE_FIM_ATMOSFERA) {
							System.out.println("Acabou a atmosfera acelera ao maximo");
							vessel.getControl().setThrottle(1);
							// Thread.currentThread().interrupt();
							continua = false;
						}

						Thread.sleep(500);
					} catch (RPCException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						// } catch (InterruptedException e) {
						// Thread.currentThread().interrupt();
						// throw new InterruptedException();
					}
				}

			}
		};
		t.start();
		// } catch (InterruptedException e) {
		// }
	}
}
