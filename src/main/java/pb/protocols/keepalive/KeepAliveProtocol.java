package pb.protocols.keepalive;

import java.time.Instant;
import java.util.logging.Logger;

import pb.Endpoint;
import pb.EndpointUnavailable;
import pb.Manager;
import pb.Utils;
import pb.protocols.Message;
import pb.protocols.Protocol;
import pb.protocols.IRequestReplyProtocol;

/**
 * Provides all of the protocol logic for both client and server to undertake
 * the KeepAlive protocol. In the KeepAlive protocol, the client sends a
 * KeepAlive request to the server every 20 seconds using
 * {@link pb.Utils#setTimeout(pb.protocols.ICallback, long)}. The server must
 * send a KeepAlive response to the client upon receiving the request. If the
 * client does not receive the response within 20 seconds (i.e. at the next time
 * it is to send the next KeepAlive request) it will assume the server is dead
 * and signal its manager using
 * {@link pb.Manager#endpointTimedOut(Endpoint,Protocol)}. If the server does
 * not receive a KeepAlive request at least every 20 seconds (again using
 * {@link pb.Utils#setTimeout(pb.protocols.ICallback, long)}), it will assume
 * the client is dead and signal its manager. Upon initialisation, the client
 * should send the KeepAlive request immediately, whereas the server will wait
 * up to 20 seconds before it assumes the client is dead. The protocol stops
 * when a timeout occurs.
 * 
 * @see {@link pb.Manager}
 * @see {@link pb.Endpoint}
 * @see {@link pb.protocols.Message}
 * @see {@link pb.protocols.keepalive.KeepAliveRequest}
 * @see {@link pb.protocols.keepalive.KeepaliveRespopnse}
 * @see {@link pb.protocols.Protocol}
 * @see {@link pb.protocols.IRequestReqplyProtocol}
 * @author aaron
 *
 */
public class KeepAliveProtocol extends Protocol implements IRequestReplyProtocol {
	private static Logger log = Logger.getLogger(KeepAliveProtocol.class.getName());
	
	/**
	 * Name of this protocol. 
	 */
	public static final String protocolName="KeepAliveProtocol";
	
    /*
     * Indicate if the connection between this endpoint and the other one is
     * alive or not.
     */
    private boolean isAlive = false;

    /*
     * Indicate if the protocol is running or not.
     */
    private boolean protocolRunning = false;

	/**
	 * Initialise the protocol with an endopint and a manager.
	 * @param endpoint
	 * @param manager
	 */
	public KeepAliveProtocol(Endpoint endpoint, Manager manager) {
		super(endpoint,manager);
	}
	
	/**
	 * @return the name of the protocol
	 */
	@Override
	public String getProtocolName() {
		return protocolName;
	}

	/**
	 * Make the protocol not send any more messages or raise any more protocol
     * events.
	 */
	@Override
	public void stopProtocol() {
		if(protocolRunning) {
            log.severe("protocol stopped while it is still underway");
            protocolRunning = false;
        }
	}
	
	/*
	 * Interface methods
	 */
	
	/**
	 * Call checkClientTimeout() to set up a timer
     * to set up a timer.
	 */
	public void startAsServer() {
        protocolRunning = true;
	    checkClientTimeout();
    }
	
	/**
	 * Call {@link pb.Manager#endpointTimedOut(Endpoint,Protocol)} if the server does
     * not receive a KeepAlive request at least every 20 seconds.
	 */
	public void checkClientTimeout() {
        Utils.getInstance().setTimeout(()->{
            log.info(String.valueOf(protocolRunning));
            if(!protocolRunning) {
                //do not send any more messages or raise any more protocol events
            }
            else if(isAlive) {
                isAlive = false;
                startAsServer();
            }
            else {
                manager.endpointTimedOut(endpoint, this);
            }
        },20000);
	}
	
	/**
	 * Call {@link pb.protocols.keepalive.KeepAliveProcol#sendRequest(Message)} to
     * send a request every 20 second.
	 */
    public void startAsClient() throws EndpointUnavailable {
        protocolRunning = true;
        KeepAliveRequest keepAliveRequest = new KeepAliveRequest();
        sendRequest(keepAliveRequest);
        Utils.getInstance().setTimeout(()->{
            if(isAlive) {
                try {
                    isAlive = false;
                    startAsClient();
                } catch(EndpointUnavailable e) {
                }
            }
            else if(!protocolRunning) {
                //do not send any more messages or raise any more protocol events
            }
            else {
                manager.endpointTimedOut(endpoint, this);
            }
        },20000);
    }

	/**
	 * Send a request.
	 * @param msg
	 */
	@Override
	public void sendRequest(Message msg) throws EndpointUnavailable {
        endpoint.send(msg);
	}

	/**
	 * Set isAlive to true if receive a reply.
	 * @param msg
	 */
	@Override
	public void receiveReply(Message msg) throws EndpointUnavailable {
        isAlive = true;
    }

	/**
	 * Set isAlive to true if receive a request.
	 * @param msg
	 * @throws EndpointUnavailable 
	 */
	@Override
	public void receiveRequest(Message msg) throws EndpointUnavailable {
        KeepAliveReply keepAliveReply = new KeepAliveReply();
        sendReply(keepAliveReply);
	    isAlive = true;
    }

	/**
	 * Send a reply.
	 * @param msg
	 */
	@Override
	public void sendReply(Message msg) throws EndpointUnavailable {
        endpoint.send(msg);
	}
	
	
}
