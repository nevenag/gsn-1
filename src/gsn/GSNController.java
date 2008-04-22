package gsn;

import gsn.beans.Modifications;
import gsn.beans.VSensorConfig;
import gsn.utils.ValidityTools;
import gsn.utils.graph.Graph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.hsqldb.lib.Iterator;

public class GSNController extends Thread {

	private ServerSocket mySocket;

	public static final int GSN_CONTROL_PORT = 22222;

	private static final int GSN_CONTROL_READ_TIMEOUT = 20000;

	public static final String GSN_CONTROL_SHUTDOWN = "GSN STOP";
	
	public static final String GSN_CONTROL_REGISTER_SHUTDOWN = "GSN REGISTER";

	public static final String GSN_CONTROL_LIST_LOADED_VSENSORS = "LIST LOADED VSENSORS";

	private boolean running = true;

	public static transient Logger logger = Logger.getLogger(GSNController.class);

	private VSensorLoader vsLoader;
	
	protected static ArrayList<ConnectionManager> safeStoragesControllers;

	public GSNController(VSensorLoader vsLoader) throws UnknownHostException, IOException {
		safeStoragesControllers = new ArrayList<ConnectionManager>();
		this.vsLoader = vsLoader;
		mySocket = new ServerSocket(GSN_CONTROL_PORT, 0, InetAddress.getByName("localhost"));
		this.start();
	}

	public void run() {
		logger.info("Started GSN Controller on port " + GSN_CONTROL_PORT);
		while (running) {
			try {
				Socket socket = mySocket.accept();
				if (logger.isDebugEnabled())
					logger.debug("Opened connection on control socket.");
				socket.setSoTimeout(GSN_CONTROL_READ_TIMEOUT);

				// Only connections from localhost are allowed
				if (ValidityTools.isLocalhost(socket.getInetAddress().getHostAddress()) == false) {
					try {
						logger.warn("Connection request from IP address >" + socket.getInetAddress().getHostAddress() + "< was denied.");
						socket.close();
					} catch (IOException ioe) {
						// do nothing
					}
					continue;
				}

				new ConnectionManager(socket).start();
			} catch (SocketTimeoutException e) {
				if (logger.isDebugEnabled())
					logger.debug("Connection timed out. Message was: " + e.getMessage());
			} catch (IOException e) {
				logger.warn("Error while accepting control connection: " + e.getMessage());
			}
		}
	}

	/*
	 * This method must be called after virtual sensors initialization. It
	 * allows GSNController to shut down properly all the virtual sensors in
	 * use.
	 */
	public void setLoader(VSensorLoader vsLoader) {
		if (this.vsLoader == null) // override protection
			this.vsLoader = vsLoader;
	}

	private class ConnectionManager extends Thread {
		private Socket incoming;
		
		private PrintWriter writer;
		
		private BufferedReader reader;

		public ConnectionManager(Socket incoming) {
			this.incoming = incoming;
		}
		
		public void sendStopMessage () {
			writer.println(GSNController.GSN_CONTROL_SHUTDOWN);
		    writer.flush();
		}

		public void run() {
			try {
				reader = new BufferedReader(new InputStreamReader(incoming.getInputStream()));
				writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(incoming.getOutputStream())), true);
				ObjectOutputStream objos = null;
				String message = reader.readLine();
				while (message != null) {
					message = message.trim();
					if (logger.isDebugEnabled())
						logger.debug("Received control message: " + message);
					if (message.equalsIgnoreCase(GSN_CONTROL_SHUTDOWN)) { // We
						// stop
						// GSN
						// here
						logger.info("Shutting down GSN...");
						running = false;
						for (int i =0 ; i < GSNController.safeStoragesControllers.size() ; i++){
							GSNController.safeStoragesControllers.get(i).sendStopMessage();
						}
						
						if (vsLoader != null) {
							vsLoader.stopLoading();
							logger.info("All virtual sensors have been stopped, shutting down virtual machine.");
						} else {
							logger
									.warn("Could not shut down virtual sensors properly. We are probably exiting GSN before it has been completely initialized.");
						}
					} else if (GSN_CONTROL_LIST_LOADED_VSENSORS.equalsIgnoreCase(message)) {
						Graph<VSensorConfig> dependencyGraph = Modifications.buildDependencyGraphFromIterator(Mappings
								.getAllVSensorConfigs());
						objos = new ObjectOutputStream(incoming.getOutputStream());
						objos.writeObject(dependencyGraph);
						objos.flush();
					}
					else if (GSN_CONTROL_REGISTER_SHUTDOWN.equalsIgnoreCase(message)) {
						safeStoragesControllers.add(this);
					}
					message = reader.readLine();
				}
			} catch (IOException e) {
				logger.warn("Error while reading from or writing to control connection: " + e.getMessage());
			}
		}
	}
}
