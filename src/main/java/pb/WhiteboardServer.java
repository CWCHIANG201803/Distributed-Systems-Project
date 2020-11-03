package pb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import pb.app.Whiteboard;
import pb.app.WhiteboardApp;
import pb.managers.ServerManager;
import pb.managers.IOThread;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

import static pb.LogColor.*;

/**
 * Simple whiteboard server to provide whiteboard peer notifications.
 * @author aaron
 *
 */
public class WhiteboardServer {
	private static Logger log = Logger.getLogger(WhiteboardServer.class.getName());


	/**
	 * Emitted by a client to tell the server that a board is being shared. Argument
	 * must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String shareBoard = "SHARE_BOARD";

	/**
	 * Emitted by a client to tell the server that a board is no longer being
	 * shared. Argument must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unshareBoard = "UNSHARE_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is being shared</li>
	 * <li>to a newly connected client, it emits this event several times, for all
	 * boards that are currently known to be being shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String sharingBoard = "SHARING_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is no longer
	 * shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unsharingBoard = "UNSHARING_BOARD";

	/**
	 * Emitted by the server to a client to let it know that there was an error in a
	 * received argument to any of the events above. Argument is the error message.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String error = "ERROR";
	
	/**
	 * Default port number.
	 */
	private static int port = Utils.indexServerPort;

	//all the peers
	private static Map<String, Endpoint> clients = new HashMap<>();

	//all the sharing boards(sharer, sharingboards)
	private static Map<String, List<String>> sharingBoards = new HashMap<>();

	//the boards that are shared by each peer
	//String: board name
	//Endpoint: peers
	//private static Map<String, Endpoint> sharedBoards = new HashMap<>();

	private static void broadcast(List<String> users, Map<String,Endpoint> sessions,
			String sharer, String event, String msg){
		if(users.isEmpty())
			return;
		else{
			for(int i = 0; i < users.size(); i++) {
				String client = users.get(i);
				if(!client.equals(sharer)) {
					sessions.get(client).emit(event, msg);
				}
			}
		}
	}

	private static void displayClientAlive(){

		if(clients.size()>0){
			log.info(ANSI_GREEN + "--------------------start--------------------" + ANSI_RESET);
			log.info(ANSI_GREEN + "The following is the client alive" + ANSI_RESET);
			for(var client : clients.keySet()){
				log.info(ANSI_CYAN + client + ANSI_RESET);
			}
			log.info(ANSI_GREEN + "--------------------end--------------------" + ANSI_RESET);
		}else{
			log.info(ANSI_RED + "oh.. it is empty now.." + ANSI_RESET);
		}
	}

	private static void displaySharedBoards(){
		if(sharingBoards.size()>0){
			log.info(ANSI_CYAN + "--------------------start--------------------" + ANSI_RESET);
			log.info(ANSI_CYAN + "The following are boards shared" + ANSI_RESET);
			for(var sharer : sharingBoards.keySet()){
				log.info(ANSI_YELLOW + sharer + " : " + sharingBoards.get(sharer) + ANSI_RESET);
			}
			log.info(ANSI_CYAN + "--------------------end--------------------" + ANSI_RESET);
		}else{
			log.info(ANSI_RED + "oh.. no board to share now.." + ANSI_RESET);
		}
	}

	private static void sendSharingPeer(Endpoint endpoint) {
		for(var sharer : sharingBoards.keySet()){
			for( var board : sharingBoards.get(sharer))
			endpoint.emit(WhiteboardServer.sharingBoard, board);
		}
	}
	
	private static void help(Options options){
		String header = "PB Whiteboard Server for Unimelb COMP90015\n\n";
		String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("pb.IndexServer", header, options, footer, true);
		System.exit(-1);
	}

	public static void getConnectedPeer(){
		log.info("size of structure" + clients.size());

	}

	public static void addShareBoard(String sharer, String boardToShare){
		if(!sharingBoards.containsKey(sharer)){
			sharingBoards.put(sharer, new ArrayList<>());
		}
		sharingBoards.get(sharer).add(boardToShare);
	}
	public static void removeShareBoard(String sharer, String sharingBoard){
		if(!sharingBoards.containsKey(sharer))
			return;

		sharingBoards.get(sharer).remove(sharingBoard);

		if(sharingBoards.get(sharer).isEmpty())
			sharingBoards.remove(sharer);

	}

	
	public static void main( String[] args ) throws IOException, InterruptedException
    {
    	// set a nice log format
		System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] [%4$s] %2$s: %5$s%n");
        
    	// parse command line options
        Options options = new Options();
        options.addOption("port",true,"server port, an integer");
        options.addOption("password",true,"password for server");
        
       
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
			cmd = parser.parse( options, args);
		} catch (ParseException e1) {
			help(options);
		}
        
        if(cmd.hasOption("port")){
        	try{
        		port = Integer.parseInt(cmd.getOptionValue("port"));
			} catch (NumberFormatException e){
				System.out.println("-port requires a port number, parsed: "+cmd.getOptionValue("port"));
				help(options);
			}
        }

        // create a server manager and setup event handlers
        ServerManager serverManager;
        
        if(cmd.hasOption("password")) {
        	serverManager = new ServerManager(port,cmd.getOptionValue("password"));
        } else {
        	serverManager = new ServerManager(port);
        }
        
        /**
         * TODO: Put some server related code here.
         */

		serverManager
				.on(IOThread.ioThread, (arg)->{
					log.info("using Internet address: " + (String)arg[0]);
				})
				.on(ServerManager.sessionStarted, (arg)->{
					Endpoint endpoint = (Endpoint)arg[0];
					log.info(ANSI_GREEN + "Client session started: " + endpoint.getOtherEndpointId() + ANSI_RESET);
					clients.put(endpoint.getOtherEndpointId(), endpoint);
					displayClientAlive();

					sendSharingPeer(endpoint);

					endpoint
							.on(shareBoard,(Args)->{
								String sharedBoard = WhiteboardApp.getBoardName((String)Args[0]);
								//log.info(ANSI_BLUE +"Received share request " + sharedBoard + ANSI_RESET);
								log.info(ANSI_BLUE +"Received share request " + sharedBoard + ANSI_RESET);
								String eventRaiser = endpoint.getOtherEndpointId();

								addShareBoard(eventRaiser, sharedBoard);
								displaySharedBoards();
								broadcast(new ArrayList<>(clients.keySet()), clients, eventRaiser, sharingBoard, sharedBoard);
							})
							.on(unshareBoard, (Args)->{
								String unSharedBoard = WhiteboardApp.getBoardName((String)Args[0]);
								log.info(ANSI_RED + "board not share " + unSharedBoard + ANSI_RESET);
								String eventRaiser = endpoint.getOtherEndpointId();
								removeShareBoard(eventRaiser, unSharedBoard);
								displaySharedBoards();
								broadcast(new ArrayList<>(clients.keySet()), clients, eventRaiser, unsharingBoard, unSharedBoard);
							});
				})
				.on(ServerManager.sessionStopped, (arg)->{
					Endpoint endpoint = (Endpoint)arg[0];

					log.severe(ANSI_RED + "Client session ended: " + endpoint.getOtherEndpointId() + ANSI_RESET);
					clients.remove(endpoint.getOtherEndpointId());
					sharingBoards.remove(endpoint.getOtherEndpointId());
					displayClientAlive();
				})
				.on(ServerManager.sessionError,(arg)->{
					Endpoint endpoint = (Endpoint)arg[0];
					log.severe(ANSI_YELLOW + "client " + endpoint.getOtherEndpointId() + " stopped" + ANSI_RESET);
					sharingBoards.remove(endpoint.getOtherEndpointId());
					clients.remove(endpoint.getOtherEndpointId());
					displayClientAlive();
				});
        
        // start up the server
        log.info("Whiteboard Server starting up");
        serverManager.start();
        // nothing more for the main thread to do
        serverManager.join();
        Utils.getInstance().cleanUp();
        
    }

}
