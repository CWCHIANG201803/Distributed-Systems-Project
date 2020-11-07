package pb.app;

import pb.WhiteboardServer;
import pb.managers.ClientManager;
import pb.managers.IOThread;
import pb.managers.PeerManager;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.logging.Logger;

import static pb.LogColor.*;


/**
 * Initial code obtained from:
 * https://www.ssaurel.com/blog/learn-how-to-make-a-swing-painting-and-drawing-application/
 */
public class WhiteboardApp {
	private static Logger log = Logger.getLogger(WhiteboardApp.class.getName());

	/**
	 * Emitted to another peer to subscribe to updates for the given board. Argument
	 * must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String listenBoard = "BOARD_LISTEN";

	/**
	 * Emitted to another peer to unsubscribe to updates for the given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unlistenBoard = "BOARD_UNLISTEN";

	/**
	 * Emitted to another peer to get the entire board data for a given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String getBoardData = "GET_BOARD_DATA";

	/**
	 * Emitted to another peer to give the entire board data for a given board.
	 * Argument must have format "host:port:boardid%version%PATHS".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardData = "BOARD_DATA";

	/**
	 * Emitted to another peer to add a path to a board managed by that peer.
	 * Argument must have format "host:port:boardid%version%PATH". The numeric value
	 * of version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathUpdate = "BOARD_PATH_UPDATE";

	/**
	 * Emitted to another peer to indicate a new path has been accepted. Argument
	 * must have format "host:port:boardid%version%PATH". The numeric value of
	 * version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathAccepted = "BOARD_PATH_ACCEPTED";

	/**
	 * Emitted to another peer to remove the last path on a board managed by that
	 * peer. Argument must have format "host:port:boardid%version%". The numeric
	 * value of version must be equal to the version of the board without the undo
	 * applied, i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoUpdate = "BOARD_UNDO_UPDATE";

	/**
	 * Emitted to another peer to indicate an undo has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the undo applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoAccepted = "BOARD_UNDO_ACCEPTED";

	/**
	 * Emitted to another peer to clear a board managed by that peer. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearUpdate = "BOARD_CLEAR_UPDATE";

	/**
	 * Emitted to another peer to indicate an clear has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearAccepted = "BOARD_CLEAR_ACCEPTED";

	/**
	 * Emitted to another peer to indicate a board no longer exists and should be
	 * deleted. Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardDeleted = "BOARD_DELETED";

	/**
	 * Emitted to another peer to indicate an error has occurred.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardError = "BOARD_ERROR";
	
	/**
	 * White board map from board name to board object 
	 */
	Map<String,Whiteboard> whiteboards;
	
	/**
	 * The currently selected white board
	 */
	Whiteboard selectedBoard = null;
	
	/**
	 * The peer:port string of the peer. This is synonomous with IP:port, host:port,
	 * etc. where it may appear in comments.
	 */
	String peerport="standalone"; // a default value for the non-distributed version
	
	/*
	 * GUI objects, you probably don't need to modify these things... you don't
	 * need to modify these things... don't modify these things [LOTR reference?].
	 */
	
	JButton clearBtn, blackBtn, redBtn, createBoardBtn, deleteBoardBtn, undoBtn;
	JCheckBox sharedCheckbox ;
	DrawArea drawArea;
	JComboBox<String> boardComboBox;
	boolean modifyingComboBox=false;
	boolean modifyingCheckBox=false;

	static Map<String, Endpoint> sessions = new HashMap<>();
	Map<String, List<String>> boardListeningLists = new HashMap<>();

	PeerManager peerManager = null;
	Endpoint endpointToSharePeer = null;
	Endpoint endpointToWhiteboardServer = null;

	Map<String, List<String>> sharingBoards = new HashMap<>();


	/**
	 * Initialize the white board app.
	 */
	public WhiteboardApp(int peerPort,String whiteboardServerHost, 
			int whiteboardServerPort) throws UnknownHostException, InterruptedException {
		whiteboards=new HashMap<>();
		setUpServerManager(peerPort);
		connectToServer(peerPort, whiteboardServerHost, whiteboardServerPort);
	}

	/*
	 * The server manager that the sharer used to let the other peers connect to
	 */
	private void setUpServerManager(int peerPort) {
		peerManager = new PeerManager(peerPort);
		peerManager
				.on(PeerManager.peerStarted, (args) -> {
					Endpoint endpt = (Endpoint)args[0];
					sessions.put(endpt.getOtherEndpointId(), endpt);
					log.info(ANSI_GREEN + "connecting from peer: " + endpt.getOtherEndpointId() + ANSI_RESET);
					endpt 
							.on(getBoardData,(arg)->{
								String boardName = (String)arg[0];
								onGetBoard(endpt, boardName);
							})
							.on(listenBoard,(arg)->{
								String boardToListen = (String)arg[0];
								log.info(ANSI_RED+boardToListen+ANSI_RESET);
								onBoardListen(endpt, boardToListen);
							})
							.on(boardPathUpdate, (arg)->{
								String updateData = (String)arg[0];
								onBoardPath(updateData);

								String targetBoard = getBoardName(updateData);
								boardListeningLists.get(targetBoard).forEach((receiver)->{
									Endpoint endpoint = sessions.get(receiver);
									endpoint.emit(boardPathAccepted, updateData);
								});
							})
							.on(boardUndoUpdate, (arg)->{
								String updateData = (String)arg[0];
								log.info(ANSI_YELLOW + updateData + ANSI_RESET);
								onBoardUndo(updateData);

								String targetBoard = getBoardName(updateData);
								boardListeningLists.get(targetBoard).forEach((receiver)->{
									Endpoint endpoint = sessions.get(receiver);
									endpoint.emit(boardUndoAccepted, updateData);
								});
							})
							.on(boardClearUpdate, (arg)->{
								String updateData = (String)arg[0];
								log.info(ANSI_YELLOW + updateData + ANSI_RESET);
								onBoardClear(updateData);

								String targetBoard = getBoardName(updateData);
								boardListeningLists.get(targetBoard).forEach(receiver->{
									Endpoint endpoint = sessions.get(receiver);
									endpoint.emit(boardClearAccepted, updateData);
								});
							});
				})
				.on(PeerManager.peerStopped, (args)->{
					Endpoint endpoint = (Endpoint)args[0];
					log.info("Disconnected from peer: " + endpoint.getOtherEndpointId());
					sessions.remove(endpoint.getOtherEndpointId());
					for(var board : boardListeningLists.keySet()){
						boardListeningLists.get(board).remove(endpoint.getOtherEndpointId());
					}
					log.info(ANSI_BLUE + this.boardListeningLists + ANSI_RESET);
				})
				.on(PeerManager.peerError, (args)->{
					Endpoint endpoint = (Endpoint)args[0];
					sessions.remove(endpoint.getOtherEndpointId());
				})
				.on(PeerManager.peerServerManager, (args)->{
					ServerManager serverManager = (ServerManager)args[0];
					serverManager.on(IOThread.ioThread, (args2)->{
						log.info("connected from another client");
					});
				});
		peerManager.start();
	}

	/*
	 * The client manager that the peer used to connect to the server
	 */
	private void connectToServer(int peerPort, String whiteboardServerHost, 
			int whiteboardServerPort) throws UnknownHostException, InterruptedException {
		ClientManager clientManager = peerManager.connect(whiteboardServerPort, whiteboardServerHost);

		clientManager.on(PeerManager.peerStarted, (args)->{
			log.info("connecting to whiteboard server");
			// todo: ask the server for the shared board
			this.endpointToWhiteboardServer = (Endpoint)args[0];
			log.info(ANSI_YELLOW + "endpoint id = " + endpointToWhiteboardServer.getOtherEndpointId() + ANSI_RESET);
			endpointToWhiteboardServer
					.on(WhiteboardServer.sharingBoard,(arg)->{
						String sharedBoard = (String)arg[0];
						onShareBoard(sharedBoard);
					})
					.on(WhiteboardServer.unsharingBoard, (arg)->{
						String unsharedBoard = (String)arg[0];
						onUnshareBoard(unsharedBoard);
					});
		});

		this.peerport = InetAddress.getLoopbackAddress().getHostAddress() + ":" + peerPort;
		show(peerport);

		clientManager.start();
		clientManager.join();
	}

	/******
	 * 
	 * Utility methods to extract fields from argument strings.
	 * 
	 ******/
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer:port:boardid
	 */
	public static String getBoardName(String data) {
		String[] parts=data.split("%",2);
		return parts[0];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return boardid%version%PATHS
	 */
	public static String getBoardIdAndData(String data) {
		String[] parts=data.split(":");
		return parts[2];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version%PATHS
	 */
	public static String getBoardData(String data) {
		String[] parts=data.split("%",2);
		return parts[1];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version
	 */
	public static long getBoardVersion(String data) {
		String[] parts=data.split("%",3);
		return Long.parseLong(parts[1]);
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return PATHS
	 */
	public static String getBoardPaths(String data) {
		String[] parts=data.split("%",3);
		return parts[2];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer
	 */
	public static String getIP(String data) {
		String[] parts=data.split(":");
		return parts[0];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return port
	 */
	public static int getPort(String data) {
		String[] parts=data.split(":");
		return Integer.parseInt(parts[1]);
	}
	
	/******
	 * 
	 * Methods called from events.
	 * 
	 ******/
	
	// From whiteboard peer

	/*
	 * Called by the sharer in setUpServerManager. This function
	 * emits the content of the sharing board.
	 */
	private void onGetBoard(Endpoint endpoint, String boardName) {
		log.info(ANSI_YELLOW + boardName + ANSI_RESET);
		String sharedBoardData = whiteboards.get(boardName).toString();
		endpoint.emit(boardData, sharedBoardData);
	}

	/*
	 * Called by the receiver to get the content on the shared board
	 * from the sharer. After getting data, this function emits listenBoard
	 * to tell the sharer that the receiver is listening to this whiteboard.
	 */
	private void onBoardData(Endpoint endpoint, String data){
		log.info(ANSI_YELLOW + data + ANSI_RESET);
		String boardName = getBoardName(data);
		String path = getBoardPaths(data);
		long version = getBoardVersion(data);

		selectedBoard.whiteboardFromString(boardName, version + "%" + path);
		drawSelectedWhiteboard();

		endpoint.emit(listenBoard, boardName);
	}

	/*
	 * Called by the sharer to add a listener into the list of the sharing whiteboard.
	 */
	private void onBoardListen(Endpoint endpoint, String boardToListen){
		log.info(ANSI_GREEN + "onBoardListen: " + boardToListen + ANSI_RESET);
		if(!boardListeningLists.containsKey(boardToListen)){
			boardListeningLists.put(boardToListen, new ArrayList<>());
		}
		String listener = endpoint.getOtherEndpointId();
		boardListeningLists.get(boardToListen).add(listener);

		log.info(ANSI_GREEN + boardListeningLists.get(boardToListen) + ANSI_RESET);

	}

	/*
	 * Called by the sharer to delete a listener from the list of the sharing whiteboard.
	 */
	private void onBoardUnListen(String boardNotToListen) {
		log.info(ANSI_RED + "onBoardUnlisten: " + boardNotToListen + ANSI_RESET);
		boardListeningLists.remove(boardNotToListen);
	}

	/*
	 * Called by the receiver to delete the board shared by the closing sharer.
	 */
	private void onBoardDeleted(String boardToDelete){
		log.info(ANSI_RED + "onBoardDeleted: " + boardToDelete + ANSI_RESET);
		deleteBoard(boardToDelete);
	}

	/*
	 * Called by receiver or sharer to update the path drew by the sharer or receiver.
	 */
	private void onBoardPath(String boardDataToRender){
		log.info(ANSI_CYAN + "onBoardPath : " + boardDataToRender + ANSI_RESET);

		String boardToUpdate = getBoardName(boardDataToRender);
		String path = getBoardPaths(boardDataToRender);
		long version = getBoardVersion(boardDataToRender);
		String currentBoard = selectedBoard.getName();

		if(currentBoard.equals(boardToUpdate)){
			selectedBoard.whiteboardFromString(boardToUpdate, version + "%" + path);
			drawSelectedWhiteboard();
		}
	}

	/*
	 * Called by receiver or sharer to clear the path.
	 */
	private void onBoardClear(String boardDataToRender) {
		log.info(ANSI_CYAN + "onBoardClear : " + boardDataToRender + ANSI_RESET);

		String boardToUpdate = getBoardName(boardDataToRender);
		String path = getBoardPaths(boardDataToRender);
		long version = getBoardVersion(boardDataToRender);
		String currentBoard = selectedBoard.getName();

		if(currentBoard.equals(boardToUpdate)){
			selectedBoard.whiteboardFromString(boardToUpdate, version + "%" + path);
			drawSelectedWhiteboard();
		}
	}

	/*
	 * Called by receiver or sharer to undo.
	 */
	private void onBoardUndo(String boardDataToRender) {
		log.info(ANSI_CYAN + "get board data : " + boardDataToRender + ANSI_RESET);

		String boardToUpdate = getBoardName(boardDataToRender);
		String path = getBoardPaths(boardDataToRender);
		long version = getBoardVersion(boardDataToRender);
		String currentBoard = selectedBoard.getName();

		if(currentBoard.equals(boardToUpdate)){
			selectedBoard.whiteboardFromString(boardToUpdate, version + "%" + path);
			drawSelectedWhiteboard();
		}
	}

	// From whiteboard server

	private void onShareBoard(String sharedBoard){
		log.info(ANSI_CYAN + getBoardName(sharedBoard) + ANSI_RESET);
		String sharer = getIP(sharedBoard)+":"+getPort(sharedBoard);

		if(!sharingBoards.containsKey(sharer)) {
			sharingBoards.put(sharer, new ArrayList<>());
		}
		sharingBoards.get(sharer).add(sharedBoard);

		Whiteboard board = new Whiteboard(getBoardName(sharedBoard), true);
		addBoard(board, false);
	}

	private void onUnshareBoard(String unsharedBoard) {
		log.info(ANSI_CYAN + getBoardName(unsharedBoard) + ANSI_RESET);
		String sharer = getIP(unsharedBoard)+":"+getPort(unsharedBoard);
		if(sharingBoards.containsKey(sharer)){
			sharingBoards.get(sharer).remove(unsharedBoard);
			deleteBoard(getBoardName(unsharedBoard));
		}
	}
	
	/******
	 * 
	 * Methods to manipulate data locally. Distributed systems related code has been
	 * cut from these methods.
	 * 
	 ******/

	/**
	 * Wait for the peer manager to finish all threads.
	 */
	public void waitToFinish() {
		Utils.getInstance().cleanUp();
	}
	
	/**
	 * Add a board to the list that the user can select from. If select is
	 * true then also select this board.
	 * @param whiteboard
	 * @param select
	 */
	public void addBoard(Whiteboard whiteboard,boolean select) {
		synchronized(whiteboards) {
			whiteboards.put(whiteboard.getName(), whiteboard);
		}
		updateComboBox(select?whiteboard.getName():null);
	}
	
	/**
	 * Delete a board from the list.
	 * @param boardname must have the form peer:port:boardid
	 */
	public void deleteBoard(String boardname) {
		synchronized(whiteboards) {
			Whiteboard whiteboard = whiteboards.get(boardname);
			if(whiteboard!=null) {
				whiteboards.remove(boardname);
			}
		}
		updateComboBox(null);
	}
	
	/**
	 * Create a new local board with name peer:port:boardid.
	 * The boardid includes the time stamp that the board was created at.
	 */
	public void createBoard() {
		String name = peerport+":board"+Instant.now().toEpochMilli();
		Whiteboard whiteboard = new Whiteboard(name,false);
		addBoard(whiteboard,true);
	}
	
	/**
	 * Add a path to the selected board. The path has already
	 * been drawn on the draw area; so if it can't be accepted then
	 * the board needs to be redrawn without it.
	 * @param currentPath
	 */
	public void pathCreatedLocally(WhiteboardPath currentPath) {
		// yes, path update event comes from here
		if(selectedBoard!=null) {
			if(!selectedBoard.addPath(currentPath,selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard(); // just redraw the screen without the path
			} else {
				// was accepted locally, so do remote stuff if needed
				String shareBoardData = selectedBoard.toString();
				log.info(ANSI_CYAN + (selectedBoard.isRemote() ? "remote" : "local") + ANSI_RESET);

				if(selectedBoard.isRemote()){		// receiver updates, so send an event to the sharer
					endpointToSharePeer.emit(boardPathUpdate, shareBoardData);
				}else{	// sharer updates
					String sharedBoardName = selectedBoard.getName();
					boolean hasListeners = boardListeningLists.get(sharedBoardName)!=null
							&& !boardListeningLists.get(sharedBoardName).isEmpty();

					if(selectedBoard.isShared() && hasListeners){
						boardListeningLists
								.get(sharedBoardName)
								.forEach(receiver-> sessions.get(receiver).emit(boardPathAccepted, shareBoardData));
					}
					drawSelectedWhiteboard();
				}
			}
		} else {
			log.severe("path created without a selected board: "+currentPath);
		}
	}
	
	/**
	 * Clear the selected whiteboard.
	 */
	public void clearedLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.clear(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				// was accepted locally, so do remote stuff if needed
				String shareBoardData = selectedBoard.toString();
				log.info(ANSI_CYAN + (selectedBoard.isRemote() ? "remote" : "local") + ANSI_RESET);

				if(selectedBoard.isRemote()){
					endpointToSharePeer.emit(boardClearUpdate, shareBoardData);
				}else{
					String sharedBoardName = selectedBoard.getName();
					boolean hasListeners = boardListeningLists.get(sharedBoardName)!=null
							&& !boardListeningLists.get(sharedBoardName).isEmpty();

					if(selectedBoard.isShared() && hasListeners){
						boardListeningLists
								.get(sharedBoardName)
								.forEach(receiver->sessions.get(receiver).emit(boardClearAccepted, shareBoardData));
					}
					drawSelectedWhiteboard();
				}
			}
		} else {
			log.severe("cleared without a selected board");
		}
	}
	
	/**
	 * Undo the last path of the selected whiteboard.
	 */
	public void undoLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.undo(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				String shareBoardData = selectedBoard.toString();
				log.info(ANSI_CYAN + (selectedBoard.isRemote() ? "remote" : "local") + ANSI_RESET);
				if(selectedBoard.isRemote()){
					endpointToSharePeer.emit(boardUndoUpdate, shareBoardData);
				}else{
					String sharedBoardName = selectedBoard.getName();
					boolean hasListeners = boardListeningLists.get(sharedBoardName)!=null
							&& !boardListeningLists.get(sharedBoardName).isEmpty();

					if(selectedBoard.isShared() && hasListeners){
						boardListeningLists
								.get(sharedBoardName)
								.forEach((receiver)->sessions.get(receiver).emit(boardUndoAccepted, shareBoardData));
					}
					drawSelectedWhiteboard();
				}
			}
		} else {
			log.severe("undo without a selected board");
		}
	}
	
	/**
	 * The variable selectedBoard has been set.
	 */
	public void selectedABoard() {
		drawSelectedWhiteboard();
		log.info("selected board: "+selectedBoard.getName() + "is it shared ? " + selectedBoard.isShared());

		if(selectedBoard.isRemote()){
			String selectedBoardName = selectedBoard.getName();

			String peerIP = getIP(selectedBoardName);
			int peerServerPort = getPort(selectedBoardName);
			String boardID = getBoardIdAndData(selectedBoardName);

			try {
				log.info("try to connect");
				log.info(ANSI_GREEN + boardID + ANSI_RESET);
				// the client manager that the receiver uses to connect to the server
				ClientManager clientMangr = peerManager.connect(peerServerPort, peerIP);
				clientMangr
						.on(PeerManager.peerStarted, (args)->{
							endpointToSharePeer = (Endpoint)args[0];
							log.info(ANSI_YELLOW + "the endpoint id of the sharing cohort " + endpointToSharePeer.getOtherEndpointId() + ANSI_RESET);
							endpointToSharePeer
									.on(boardData, (args1)->{
										String boardDataToRender = (String)args1[0];
										onBoardData(endpointToSharePeer, boardDataToRender);
									})
									.on(boardPathAccepted, (args1)->{
										String boardDataToRender =(String)args1[0];
										onBoardPath(boardDataToRender);
									})
									.on(boardClearAccepted, (args1)->{
										String boardDataToRender =(String)args1[0];
										onBoardClear(boardDataToRender);
									})
									.on(boardUndoAccepted, (args1)->{
										String boardDataToRender =(String)args1[0];
										onBoardUndo(boardDataToRender);
									})
									.on(unlistenBoard,(args1)->{
										String boardNotToListen = (String)args1[0];
										onBoardUnListen(boardNotToListen);
									})
									.on(boardDeleted, (args1)->{
										String boardToDelete = (String)args1[0];
										onBoardDeleted(boardToDelete);
										clientMangr.shutdown();
									})
									.on(boardError, (args1)->{
										String boardError = (String)args1[0];
									});
							endpointToSharePeer.emit(getBoardData, selectedBoardName);
						})
						.on(PeerManager.peerStopped, (args)->{
							Endpoint endpoint = (Endpoint)args[0];
							clientMangr.endpointClosed(endpoint);
							log.info(ANSI_YELLOW + "peer has terminated :" + endpoint.getOtherEndpointId() +ANSI_RESET);
						});
				clientMangr.start();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
	}

	/**
	 * Set the share status on the selected board.
	 */
	public void setShare(boolean share) {
		if(selectedBoard!=null) {
			log.info(ANSI_GREEN + "I would like to share this board to others!" + ANSI_RESET);
        	selectedBoard.setShared(share);
        	String targetBoard = selectedBoard.getName();

        	if(share) {
        		log.info("send share event to server");
				endpointToWhiteboardServer.emit(WhiteboardServer.shareBoard, targetBoard);
			} else {
				endpointToWhiteboardServer.emit(WhiteboardServer.unshareBoard, targetBoard);
			}
		} else {
        	log.severe("there is no selected board");
        }
	}
	
	/**
	 * Called by the gui when the user closes the app.
	 */
	public void guiShutdown() {
		// do some final cleanup
		log.info(ANSI_CYAN + " number of sharing board: " + boardListeningLists.size() + ANSI_RESET);
		for(var whiteboard : whiteboards.values()){
			endpointToWhiteboardServer.emit(WhiteboardServer.unshareBoard, whiteboard.getName());

			sessions.values().forEach(endpt->{
				if(whiteboard.isShared()) {
					endpt.emit(unlistenBoard, whiteboard.getName());
					endpt.emit(boardDeleted, whiteboard.getName());
				}
			});
		}
		HashSet<Whiteboard> existingBoards= new HashSet<>(whiteboards.values());
		existingBoards.forEach((board)->{
			deleteBoard(board.getName());
		});

		whiteboards.values().forEach((whiteboard)->{
    	});
		peerManager.shutdown();
	}
	
	

	/******
	 * 
	 * GUI methods and callbacks from GUI for user actions.
	 * You probably do not need to modify anything below here.
	 * 
	 ******/
	
	/**
	 * Redraw the screen with the selected board
	 */
	public void drawSelectedWhiteboard() {
		drawArea.clear();
		if(selectedBoard!=null) {
			selectedBoard.draw(drawArea);
		}
	}
	
	/**
	 * Setup the Swing components and start the Swing thread, given the
	 * peer's specific information, i.e. peer:port string.
	 */
	public void show(String peerport) {
		// create main frame
		JFrame frame = new JFrame("Whiteboard Peer: "+peerport);
		Container content = frame.getContentPane();
		// set layout on content pane
		content.setLayout(new BorderLayout());
		// create draw area
		drawArea = new DrawArea(this);

		// add to content pane
		content.add(drawArea, BorderLayout.CENTER);

		// create controls to apply colors and call clear feature
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

		/**
		 * Action listener is called by the GUI thread.
		 */
		ActionListener actionListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == clearBtn) {
					clearedLocally();
				} else if (e.getSource() == blackBtn) {
					drawArea.setColor(Color.black);
				} else if (e.getSource() == redBtn) {
					drawArea.setColor(Color.red);
				} else if (e.getSource() == boardComboBox) {
					if(modifyingComboBox) return;
					if(boardComboBox.getSelectedIndex()==-1) return;
					String selectedBoardName=(String) boardComboBox.getSelectedItem();
					if(whiteboards.get(selectedBoardName)==null) {
						log.severe("selected a board that does not exist: "+selectedBoardName);
						return;
					}
					selectedBoard = whiteboards.get(selectedBoardName);
					// remote boards can't have their shared status modified
					if(selectedBoard.isRemote()) {
						sharedCheckbox.setEnabled(false);
						sharedCheckbox.setVisible(false);
					} else {
						modifyingCheckBox=true;
						sharedCheckbox.setSelected(selectedBoard.isShared());
						modifyingCheckBox=false;
						sharedCheckbox.setEnabled(true);
						sharedCheckbox.setVisible(true);
					}
					selectedABoard();
				} else if (e.getSource() == createBoardBtn) {
					createBoard();
				} else if (e.getSource() == undoBtn) {
					if(selectedBoard==null) {
						log.severe("there is no selected board to undo");
						return;
					}
					undoLocally();
				} else if (e.getSource() == deleteBoardBtn) {
					if(selectedBoard==null) {
						log.severe("there is no selected board to delete");
						return;
					}
					deleteBoard(selectedBoard.getName());
				}
			}
		};
		
		clearBtn = new JButton("Clear Board");
		clearBtn.addActionListener(actionListener);
		clearBtn.setToolTipText("Clear the current board - clears remote copies as well");
		clearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		blackBtn = new JButton("Black");
		blackBtn.addActionListener(actionListener);
		blackBtn.setToolTipText("Draw with black pen");
		blackBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		redBtn = new JButton("Red");
		redBtn.addActionListener(actionListener);
		redBtn.setToolTipText("Draw with red pen");
		redBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		deleteBoardBtn = new JButton("Delete Board");
		deleteBoardBtn.addActionListener(actionListener);
		deleteBoardBtn.setToolTipText("Delete the current board - only deletes the board locally");
		deleteBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		createBoardBtn = new JButton("New Board");
		createBoardBtn.addActionListener(actionListener);
		createBoardBtn.setToolTipText("Create a new board - creates it locally and not shared by default");
		createBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		undoBtn = new JButton("Undo");
		undoBtn.addActionListener(actionListener);
		undoBtn.setToolTipText("Remove the last path drawn on the board - triggers an undo on remote copies as well");
		undoBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		sharedCheckbox = new JCheckBox("Shared");
		sharedCheckbox.addItemListener(new ItemListener() {    
	         public void itemStateChanged(ItemEvent e) { 
	            if(!modifyingCheckBox) setShare(e.getStateChange()==1);
	         }    
	      }); 
		sharedCheckbox.setToolTipText("Toggle whether the board is shared or not - tells the whiteboard server");
		sharedCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);
		

		// create a drop list for boards to select from
		JPanel controlsNorth = new JPanel();
		boardComboBox = new JComboBox<String>();
		boardComboBox.addActionListener(actionListener);
		
		
		// add to panel
		controlsNorth.add(boardComboBox);
		controls.add(sharedCheckbox);
		controls.add(createBoardBtn);
		controls.add(deleteBoardBtn);
		controls.add(blackBtn);
		controls.add(redBtn);
		controls.add(undoBtn);
		controls.add(clearBtn);

		// add to content pane
		content.add(controls, BorderLayout.WEST);
		content.add(controlsNorth,BorderLayout.NORTH);
		frame.setSize(600, 600);
		
		// create an initial board
		createBoard();
		
		// closing the application
		frame.addWindowListener(new WindowAdapter() {
		    @Override
		    public void windowClosing(WindowEvent windowEvent) {
		        if (JOptionPane.showConfirmDialog(frame, 
		            "Are you sure you want to close this window?", "Close Window?", 
		            JOptionPane.YES_NO_OPTION,
		            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
		        {
		        	guiShutdown();
		            frame.dispose();
		        }
		    }
		});
		
		// show the swing paint result
		frame.setVisible(true);
		
	}
	
	/**
	 * Update the GUI's list of boards. Note that this method needs to update data
	 * that the GUI is using, which should only be done on the GUI's thread, which
	 * is why invoke later is used.
	 * 
	 * @param select, board to select when list is modified or null for default
	 *                selection
	 */
	private void updateComboBox(String select) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				modifyingComboBox=true;
				boardComboBox.removeAllItems();
				int anIndex=-1;
				synchronized(whiteboards) {
					ArrayList<String> boards = new ArrayList<String>(whiteboards.keySet());
					Collections.sort(boards);
					for(int i=0;i<boards.size();i++) {
						String boardname=boards.get(i);
						boardComboBox.addItem(boardname);
						if(select!=null && select.equals(boardname)) {
							anIndex=i;
						} else if(anIndex==-1 && selectedBoard!=null && 
								selectedBoard.getName().equals(boardname)) {
							anIndex=i;
						} 
					}
				}
				modifyingComboBox=false;
				if(anIndex!=-1) {
					boardComboBox.setSelectedIndex(anIndex);
				} else {
					if(whiteboards.size()>0) {
						boardComboBox.setSelectedIndex(0);
					} else {
						drawArea.clear();
						createBoard();
					}
				}
				
			}
		});
	}
	
}
