package pb.client;


import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import pb.Endpoint;
import pb.EndpointUnavailable;
import pb.Manager;
import pb.ProtocolAlreadyRunning;
import pb.Utils;
import pb.protocols.IRequestReplyProtocol;
import pb.protocols.Protocol;
import pb.protocols.keepalive.KeepAliveProtocol;
import pb.protocols.session.SessionProtocol;

/**
 * Manages the connection to the server and the client's state.
 * 
 * @see {@link pb.Manager}
 * @see {@link pb.Endpoint}
 * @see {@link pb.protocols.Protocol}
 * @see {@link pb.protocols.IRequestReplyProtocol}
 * @author aaron
 *
 */
public class ClientManager extends Manager {
	private static Logger log = Logger.getLogger(ClientManager.class.getName());
	private SessionProtocol sessionProtocol;
	private KeepAliveProtocol keepAliveProtocol;
	private Socket socket;
	private int retryCountMax=10;
	private long numMillisecondsToSleep =5000;
	private String host;
	private int port;
	
	public ClientManager(String host,int port) throws UnknownHostException, IOException {
		this.port=port;
		this.host=host;
		int count=1;
		while (socket ==null && count<retryCountMax+1) {
			try {
				log.info("number of attemps: "+count);
				socket=new Socket(InetAddress.getByName(host),port);
			}catch(Exception e){
				try {
					Thread.sleep(numMillisecondsToSleep);
				}catch(InterruptedException interruptedexpection){
					interruptedexpection.printStackTrace();
				}
				log.severe("well...still can't connect!");
				count++;
			}
		}
		if (socket==null) {
			log.severe("Give up...");
			System.exit(-1);
		}
		
		Endpoint endpoint = new Endpoint(socket,this);
		endpoint.start();
		try {
			// just wait for this thread to terminate
			endpoint.join();
		} catch (InterruptedException e) {
			// just make sure the ioThread is going to terminate
			endpoint.close();
		}	
	}
	
	/** 
	 * When disconnection from server happened,
	 * this method will be called to retry connection
	 * for 10 times per 5 seconds before giving up.
	 */
	private void retryConnect() {
		int count=0;
		while (count<retryCountMax+1) {
			try {
				if(count!=0) {
					log.info("number of attemps: "+count);
					}
				socket=new Socket(InetAddress.getByName(host),port);
				Endpoint endpoint1 = new Endpoint(socket,this);
				endpoint1.start();
				try {
					endpoint1.join();
				} catch (Exception exception) {
					//exception.printStackTrace();
					endpoint1.close();
				}
			}catch(IOException ioexpection){
				try {
					Thread.sleep(numMillisecondsToSleep);
				}catch(InterruptedException interruptedexpection){
					//interruptedexpection.printStackTrace();
				}
				if(count!=0) {
					log.severe("well...still can't connect");
					}
				count++;
			}
		}
		log.severe("Give up...");
		System.exit(-1);
		}
	

	/**
	 * The endpoint is ready to use.
	 * @param endpoint
	 */
	@Override
	public void endpointReady(Endpoint endpoint) {
		log.info("connection with server established");
		sessionProtocol = new SessionProtocol(endpoint,this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(sessionProtocol);
			sessionProtocol.startAsClient();
		} catch (EndpointUnavailable e) {
			log.severe("connection with server terminated abruptly");
			endpoint.close();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm, so the server is requesting a session start?
			log.warning("server initiated the session protocol... weird");
		}
		keepAliveProtocol = new KeepAliveProtocol(endpoint,this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(keepAliveProtocol);
			keepAliveProtocol.startAsClient();
		} catch (EndpointUnavailable e) {
			log.severe("connection with server terminated abruptly");
			endpoint.close();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm, so the server is requesting a session start?
			log.warning("server initiated the session protocol... weird");
		}
	}
	
	/**
	 * The endpoint close() method has been called and completed.
	 * @param endpoint
	 */
	public void endpointClosed(Endpoint endpoint) {
		log.info("connection with server terminated");
		}
	
	/**
	 * The endpoint has abruptly disconnected. It will call
	 * retryConnect() to try to reconnect.
	 * @param endpoint
	 */
	@Override
	public void endpointDisconnectedAbruptly(Endpoint endpoint) {
		log.severe("connection with server terminated abruptly");
		endpoint.close();
		retryConnect();
		}
	

	/**
	 * An invalid message was received over the endpoint.
	 * @param endpoint
	 */
	@Override
	public void endpointSentInvalidMessage(Endpoint endpoint) {
		log.severe("server sent an invalid message");
		endpoint.close();
	}
	

	/**
	 * The protocol on the endpoint is not responding.
	 * @param endpoint
	 */
	@Override
	public void endpointTimedOut(Endpoint endpoint,Protocol protocol) {
		log.severe("server has timed out");
		endpoint.close();
	}

	/**
	 * The protocol on the endpoint has been violated.
	 * @param endpoint
	 */
	@Override
	public void protocolViolation(Endpoint endpoint,Protocol protocol) {
		log.severe("protocol with server has been violated: "+protocol.getProtocolName());
		endpoint.close();
	}

	/**
	 * The session protocol is indicating that a session has started.
	 * @param endpoint
	 */
	@Override
	public void sessionStarted(Endpoint endpoint) {
		log.info("session has started with server");
		
		// we can now start other protocols with the server
	}

	/**
	 * The session protocol is indicating that the session has stopped. 
	 * @param endpoint
	 */
	@Override
	public void sessionStopped(Endpoint endpoint) {
		log.info("session has stopped with server");
		endpoint.close(); // this will stop all the protocols as well
	}
	

	/**
	 * The endpoint has requested a protocol to start. If the protocol
	 * is allowed then the manager should tell the endpoint to handle it
	 * using {@link pb.Endpoint#handleProtocol(Protocol)}
	 * before returning true.
	 * @param protocol
	 * @return true if the protocol was started, false if not (not allowed to run)
	 */
	@Override
	public boolean protocolRequested(Endpoint endpoint, Protocol protocol) {
		// the only protocols in this system are this kind...
		try {
			((IRequestReplyProtocol)protocol).startAsClient();
			endpoint.handleProtocol(protocol);
			return true;
		} catch (EndpointUnavailable e) {
			// very weird... should log this
			return false;
		} catch (ProtocolAlreadyRunning e) {
			// even more weird... should log this too
			return false;
		}
	}

}
