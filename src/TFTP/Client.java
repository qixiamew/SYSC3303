package TFTP;
/* Client class for Iteration 1
 * Team 5 - 5000000
 * @author: team 5
 */

/* the following code deals with the client part of this exercise.
 *in the following exercise the client is send a Read Write or Test message to the Errsim which then be sended to the server
 * further explanation about how the connection between the errSim and the server will is explained in the two other classes.*/
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Scanner;

import exceptions.ErrorException;
import exceptions.ReceivedErrorException;
import exceptions.UnknownIDException;

public class Client {
	DatagramPacket sendPacket, receivePacket; // creat two DatagramPacket to
												// send and receive data from
												// and to the ErrSim
	DatagramSocket sendReceiveSocket; // We only need one datagramsocket since
										// we are never //sending and receiving
										// at the same time
	private Scanner input;
	private BufferedInputStream in; // stream to read in file

	public static enum Mode {
		NORMAL, TEST
	}; // enum serving for different mode

	public static enum Decision {
		RRQ, WRQ
	}; // same for decision both enum are inputted in the consol of the client

	private static String fname;

	private static final String CLIENT_DIRECTORY = "C:\\Users\\Public\\";
	private static final int DATA_SIZE = 512;
	private static final int PACKET_SIZE = 516;
	private static int timeoutLim = 2;
	private int timeout = 0;
	private boolean resending = false;
	private int currentBlock = 1;

	private static final int ILLEGAL_OPER_ERR_CODE = 4;
	private static final int UNKNOWN_TRANSFER_ID_ERR_CODE = 5;

	private static final int SERVER_LISTENER = 69;
	private static final int INTERMEDIARY_LISTENER = 69;
	private int dataSize = 512;
	private boolean transfering = true;
	private boolean isNewData = true;
	private InetAddress serverAddress;
	private int serverPort;

	public Client() {
		try {
			sendReceiveSocket = new DatagramSocket(); // creat the datagram
			sendReceiveSocket.setSoTimeout(2000); // socket
		} catch (SocketException se) { // catch Socket exception error if
										// applicable
			se.printStackTrace();
			System.exit(1);
		}
	}

	public static void main(String args[]) throws IOException {
		Client c = new Client();
		System.out.println("open Client Program!\n");
		c.inter();
	}

	public void inter() throws IOException {
		String mode = "netascii"; // The used mode
		Decision request = Decision.RRQ; // default decision which is Read
		input = new Scanner(System.in); // run a new scanner to scan the input
										// from the user

		System.out.println("choose (R)ead Request, (W)rite Request, or (Q)uit?");
		String choice = input.nextLine(); // reads the input String

		// it runs threw all the possible answers if none are applicable it
		// recursively go back to inter()
		if (choice.equalsIgnoreCase("R")) {
			request = Decision.RRQ;
			System.out.println("Client: send a read request.");
		} else if (choice.equalsIgnoreCase("W")) {
			request = Decision.WRQ;
			System.out.println("Client:  send a write request.");
		} else if (choice.equalsIgnoreCase("Q")) {
			System.out.println("Goodbye!");
			System.exit(1);
		} else {
			System.out.println("invalid choice.  Please try again...");
			inter();
		}

		// gets a file directory from the user
		System.out.println("Please choose a file to modify.  Type in a file name: ");

		fname = input.nextLine();

		DatagramPacket requestPacket = buildRequest(fname.getBytes());

		// decide if it s a read or a write
		try {

			if (request == Decision.RRQ) {
				System.out.println("Client:" + fname + ", receive in " + mode + " mode.\n");
				read(requestPacket);

			} else if (request == Decision.WRQ) {
				System.out.println("Client:" + fname + ", send in " + mode + " mode.\n");
				write(requestPacket);
			}
		} catch (ReceivedErrorException e) {
			System.out.println("\nReceived a timeout error"); // fix this
		}

		catch (ErrorException e) {
			// Build the error

			System.out.println(e.getMessage());

			DatagramPacket err = buildError(e.getMessage().getBytes(), e.getErrorCode());

			// set port and address
			err.setAddress(serverAddress);
			err.setPort(serverPort);

			// Send error
			try {
				sendReceiveSocket.send(err);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}

	/**
	 * builds a request. Opcode must be modified to read or write.
	 * 
	 * @param filename
	 * @return
	 */
	private DatagramPacket buildRequest(byte[] filename) {
		byte[] mode = ("netascii").getBytes();

		byte[] data = new byte[2 + filename.length + 1 + mode.length + 1];
		data[0] = 0;
		data[1] = 2;
		System.arraycopy(filename, 0, data, 2, filename.length);
		data[2 + filename.length] = 0;
		System.arraycopy(mode, 0, data, 3 + filename.length, mode.length);

		DatagramPacket requestPacket = new DatagramPacket(data, data.length);

		try {
			requestPacket.setAddress(InetAddress.getLocalHost());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		requestPacket.setPort(INTERMEDIARY_LISTENER);

		return requestPacket;
	}

	private DatagramPacket receiveData() throws IOException {

		byte[] data = new byte[PACKET_SIZE];

		DatagramPacket dataPacket = new DatagramPacket(data, data.length);

		sendReceiveSocket.receive(dataPacket);

		return dataPacket;
	}

	private boolean validateData(DatagramPacket dataPacket) throws ErrorException {

		int opcode = ((dataPacket.getData()[0] & 0xff) << 8) | (dataPacket.getData()[1] & 0xff);
		int dataNumber = ((dataPacket.getData()[2] & 0xff) << 8) | (dataPacket.getData()[3] & 0xff);

		InetAddress dataAddress = dataPacket.getAddress();
		int dataPort = dataPacket.getPort();

		byte[] data = dataPacket.getData();

		// Check its data
		if (opcode == 3) {
			// is data
		} else if (opcode == 5) {
			// received error packet
			throw new ReceivedErrorException(dataPacket);
		} else {
			// Not data or error
			throw new ErrorException("Received an unexpected packet. Opcode: " + opcode, ILLEGAL_OPER_ERR_CODE);
		}

		// Check Address and port
		if (dataPort != serverPort || !dataAddress.equals(serverAddress)) {
			sendUnknownIDError(dataAddress, dataPort);
			return true;
		}

		// check the packet number matches what server is expecting
		if (dataNumber < currentBlock) {
			System.out.println("received duplicate data packet");

			currentBlock--;

			// ignore and send next data
		} else if (dataNumber > currentBlock) {
			System.out.println("received data from the future");
			throw new ErrorException("received data from the future", ILLEGAL_OPER_ERR_CODE);
		}

		// System.out.println(data.length);
		if (data[data.length - 1] == (byte) 0) {
			transfering = false;
		}

		return false;

	}

	/**
	 * Read request
	 * 
	 * @param request
	 * @throws IOException
	 * @throws ErrorException
	 */
	private void read(DatagramPacket request) throws IOException, ErrorException {

		request.getData()[1] = (byte) 1;

		try {
			sendReceiveSocket.send(request);
		} catch (IOException e2) {
			e2.printStackTrace();
		}

		byte[] incomingData = new byte[PACKET_SIZE];

		DatagramPacket dataPacket = new DatagramPacket(incomingData, incomingData.length);
		DatagramPacket ackPacket = buildAckPacket(0);

		File newFile = new File(CLIENT_DIRECTORY + fname);

		if (!newFile.exists()) {
			newFile.createNewFile();
		}

		BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(newFile));

		// get data1 to save address and port

		dataPacket = receiveData();

		System.out.println("got packet: ");
		for (byte b : dataPacket.getData()) {
			System.out.print(b);
		}
		System.out.println();

		// save port and address of client
		serverAddress = dataPacket.getAddress();
		serverPort = dataPacket.getPort();

		// write data
		if (resending == false) {
			writer.write(incomingData, 4, DATA_SIZE);
		}
		// else{
		// isNewData = true;
		// }
		// build and send ack
		ackPacket = buildAckPacket(currentBlock);
		sendReceiveSocket.send(ackPacket);
		System.out.println("sending packet: ");
		for (byte b : ackPacket.getData()) {
			System.out.print(b);
		}
		System.out.println();

		currentBlock++;

		do {
			// receive and validate data
			do {
				try {
					// Receive data packet
					dataPacket = receiveData();
					System.out.println(currentBlock);
					System.out.println("got packet: ");
					for (byte b : dataPacket.getData()) {
						System.out.print(b);
					}
					System.out.println();
				} catch (SocketTimeoutException e) {
					System.out.println(

					"Timeout receiving data " + currentBlock + " resending previous ack ");

					timeout++;
					resending = true;
					if (timeout == timeoutLim) {
						throw new ErrorException("Timeout limit reached", 0);
					}
					break;
				}

			} while (validateData(dataPacket));

			serverAddress = dataPacket.getAddress();
			serverPort = dataPacket.getPort();

			incomingData = dataPacket.getData();
			int receivedblockNum = ((incomingData[2] & 0xff) << 8) | (incomingData[3] & 0xff);

			if (resending == false) {
				writer.write(incomingData, 4, DATA_SIZE);
			}
			ackPacket = buildAckPacket(receivedblockNum);

			sendReceiveSocket.send(ackPacket);
			System.out.println("sending packet: ");
			for (byte b : ackPacket.getData()) {
				System.out.print(b);
			}
			System.out.println();

			if (resending == false) {
				currentBlock++;
			}
			resending = false;
		} while (transfering);
		// transfer complete

		System.out.println("read finished");

		writer.close();

	}

	/**
	 * Write request
	 * 
	 * @param request
	 * @throws IOException
	 * @throws ErrorException
	 */
	private void write(DatagramPacket request) throws IOException, ErrorException {

		// Send write request
		request.getData()[1] = (byte) 2;
		sendReceiveSocket.send(request);

		// Receive ack 0
		DatagramPacket ackPacket = receiveAck();

		// check ack 0
		System.out.println("received: ");
		for (byte b : ackPacket.getData()) {
			System.out.print(b);
		}
		System.out.println();

		// Save server address and port
		serverPort = ackPacket.getPort();
		serverAddress = ackPacket.getAddress();

		// Set up data packet and stream to create files.
		byte[] dataForPacket = new byte[4 + dataSize];
		dataForPacket[0] = 0;
		dataForPacket[1] = 3;
		DatagramPacket dataPacket = new DatagramPacket(dataForPacket, dataForPacket.length, serverAddress, serverPort);

		in = new BufferedInputStream(new FileInputStream(CLIENT_DIRECTORY + fname));

		byte[] dataToSend = new byte[dataSize];

		// Data 1 is read
		int sizeOfDataRead = in.read(dataToSend);

		while (transfering) {
			// iterate the file in 512 byte chunks
			// Each iteration send the packet and receive the ack to match block
			// number i

			// Add block number to packet data
			dataForPacket[2] = (byte) ((currentBlock >> 8) & 0xFF);
			dataForPacket[3] = (byte) (currentBlock & 0xFF);

			// Copy the data from the file into the packet data
			System.arraycopy(dataToSend, 0, dataForPacket, 4, dataToSend.length);

			dataPacket.setData(dataForPacket);
			System.out.println("sending data " + currentBlock + " of size: " + sizeOfDataRead);

			sendReceiveSocket.send(dataPacket);

			System.out.println("sent: ");

			for (byte b : dataPacket.getData()) {
				System.out.print(b);
			}

			System.out.println();

			// Receive ack packet

			do {
				try {
					ackPacket = receiveAck();
					int ackNum = ((ackPacket.getData()[2] & 0xff) << 8) | (ackPacket.getData()[3] & 0xff);
					System.out.println("received ack " + ackNum);

					System.out.println("received: ");

					for (byte b : ackPacket.getData()) {
						System.out.print(b);
					}

					System.out.println();

				} catch (SocketTimeoutException e) {
					System.out.println("Timeout receiving ack " + currentBlock + " resending data " + (currentBlock));
					currentBlock--;
					timeout++;
					if (timeout == timeoutLim) {
						throw new ErrorException("Timeout limit reached", 0);
					}
					break;
				}

			} while (validateAck(ackPacket));

			
		
			dataToSend = new byte[dataSize];

			currentBlock++;
			sizeOfDataRead = in.read(dataToSend);
			if (sizeOfDataRead == -1) {
				// Transferring should end
				transfering = false;
			}
			else if (sizeOfDataRead <= 512){
				dataSize = sizeOfDataRead;
			}
		}
		in.close();
	}

	/**
	 * Receives ack packet
	 * 
	 * @return
	 * @throws IOException
	 */
	private DatagramPacket receiveAck() throws IOException {
		byte[] data = new byte[4];
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);

		sendReceiveSocket.receive(receivePacket);

		return receivePacket;
	}

	/**
	 * 
	 * @param ackPacket
	 * @param currentPacketNumber
	 * @return
	 */
	protected boolean validateAck(DatagramPacket ackPacket) throws ErrorException {

		int opcode = ((ackPacket.getData()[0] & 0xff) << 8) | (ackPacket.getData()[1] & 0xff);
		int ackNumber = ((ackPacket.getData()[2] & 0xff) << 8) | (ackPacket.getData()[3] & 0xff);

		InetAddress ackAddress = ackPacket.getAddress();
		int ackPort = ackPacket.getPort();

		// Check its an ack
		if (opcode == 4) {
			// is ack
		} else if (opcode == 5) {
			// received error packet
			throw new ReceivedErrorException(ackPacket);
		} else {
			// Not an ackPacket
			System.out.println("did not receive ack packet");
			throw new ErrorException("Received an unexpected packet. Opcode: " + opcode, ILLEGAL_OPER_ERR_CODE);
		}

		// Check Address and port
		if (ackPort != serverPort || !ackAddress.equals(serverAddress)) {
			sendUnknownIDError(ackAddress, ackPort);
			return true;
		}

		// check the packet number matches what server is expecting
		if (ackNumber < currentBlock) {
			System.out.println(ackNumber + "  " + currentBlock);
			System.out.println("received duplicate ack packet");
			return true;
			// ignore and send next data
		} else if (ackNumber > currentBlock) {
			System.out.println("received ack from the future");
			throw new ErrorException("received ack from the future", ILLEGAL_OPER_ERR_CODE);
		}

		return false;

	}

	/**
	 * Builds and returns an ack packet with the block number passed.
	 * 
	 * @param blockNumber
	 * @return
	 */
	private DatagramPacket buildAckPacket(int blockNumber) {
		byte[] data = new byte[4];

		data[0] = 0;
		data[1] = 4;
		data[2] = (byte) ((blockNumber >> 8) & 0xFF);
		data[3] = (byte) (blockNumber & 0xFF);

		DatagramPacket ackPack = new DatagramPacket(data, data.length);

		ackPack.setAddress(serverAddress);
		ackPack.setPort(serverPort);

		return ackPack;
	}

	protected DatagramPacket buildError(byte[] errMessage, int errCode) {

		byte[] errData = new byte[5 + errMessage.length];

		errData[0] = (byte) 0;
		errData[1] = (byte) 5;
		errData[2] = (byte) ((errCode >> 8) & 0xFF);
		errData[3] = (byte) (errCode & 0xFF);

		System.arraycopy(errMessage, 0, errData, 4, errMessage.length);

		DatagramPacket errPacket = new DatagramPacket(errData, errData.length);

		return errPacket;
	}

	protected void sendUnknownIDError(InetAddress add, int port) {
		UnknownIDException e = new UnknownIDException(add, port);
		DatagramPacket err = buildError(e.getMessage().getBytes(), e.getErrorCode());

		// Set address to other a
		err.setAddress(e.getAddress());
		err.setPort(e.getPort());

		// Send error
		try {
			sendReceiveSocket.send(err);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	public void exit() {
		sendReceiveSocket.close();
	}
}