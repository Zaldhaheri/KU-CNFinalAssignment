import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public class SMTPClient {
    private static final int SERVER_PORT = 12345;
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);
        DatagramSocket clientSocket = null;

        try {
            System.out.println("Mail Client starting on host: " + InetAddress.getLocalHost().getHostName());

            String mailServerHostname = null;
            while (mailServerHostname == null) {
                System.out.print("Enter the hostname of the Mail server: ");
                mailServerHostname = scan.nextLine();

                try {
                    InetAddress serverAddress = InetAddress.getByName(mailServerHostname);
                    if (!serverAddress.isReachable(5000)) {
                        System.out.println("Server is not reachable. Please enter a valid server hostname.");
                        mailServerHostname = null;
                    }
                } catch (UnknownHostException e) {
                    System.out.println("Invalid server hostname. Please enter a valid hostname.");
                    mailServerHostname = null;
                }
            }

            InetAddress serverAddress = InetAddress.getByName(mailServerHostname);

            clientSocket = new DatagramSocket();
            performHandshake(clientSocket, serverAddress);

            String clientType = "";
            while (!clientType.equals("sender") && !clientType.equals("receiver")) {
                System.out.print("Are you a 'sender' or 'receiver' client? ");
                clientType = scan.nextLine().toLowerCase();
            }

            if (clientType.equals("sender")) {
                new SenderClient(clientSocket, serverAddress, scan).start();
            } else {
                new ReceiverClient(clientSocket, serverAddress).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (clientSocket != null && !clientSocket.isClosed()) {
  //              clientSocket.close();
            }
            //if we close the socket, the main thread will finish before the client threads, it will cause errors and the client threads wont work
        }
    }

    private static void performHandshake(DatagramSocket clientSocket, InetAddress serverAddress) throws IOException {
        String syn = "SYN";
        byte[] sendData = syn.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
        clientSocket.send(sendPacket);

        byte[] receiveData = new byte[BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(receivePacket);
        String synAck = new String(receivePacket.getData(), 0, receivePacket.getLength());

        if (synAck.equals("SYN-ACK")) {
            String ack = "ACK";
            sendData = ack.getBytes();
            sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
            clientSocket.send(sendPacket);
            System.out.println("Connection established with the server.");
        } else {
            System.out.println("Handshake failed. Terminating...");
            System.exit(0);
        }
    }

    private static class SenderClient extends Thread {
        private final DatagramSocket clientSocket;
        private final InetAddress serverAddress;
        private final Scanner scanner;

        public SenderClient(DatagramSocket clientSocket, InetAddress serverAddress, Scanner scanner) {
            this.clientSocket = clientSocket;
            this.serverAddress = serverAddress;
            this.scanner = scanner;
        }

        public void run() {
            while (true) {
                System.out.println("Creating New Email...");
                System.out.print("To: ");
                String to = "";
                if (scanner.hasNext())
                    to = scanner.nextLine();
                else 
                    System.out.println("Dont have next;");
                System.out.print("From: ");
                String from = scanner.nextLine();
                System.out.print("Subject: ");
                String subject = scanner.nextLine();
                System.out.print("Body: ");
                String body = scanner.nextLine();

                String attachment = "";
                System.out.print("Do you want to add an attachment? (yes/no): ");
                File selectedFile = null;
                String attachmentChoice = scanner.nextLine().toLowerCase();
                if (attachmentChoice.equals("yes")) {
                    JFileChooser fileChooser = new JFileChooser();
                    FileNameExtensionFilter filter = new FileNameExtensionFilter("Attachments", "pdf", "jpg", "mp4");
                    fileChooser.setFileFilter(filter);
                    int result = fileChooser.showOpenDialog(null);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        selectedFile = fileChooser.getSelectedFile();
                        attachment = encodeFileToBase64(selectedFile);
                    }
                }

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy HH:mm:ss"));
                String sequenceNumber = String.valueOf(System.currentTimeMillis());

                String email = "FROM: " + from + "\nTO: " + to + "\nSUBJECT: " + subject + "\nTIME-STAMP: " + timestamp
                        + "\nSEQUENCE-NUMBER: " + sequenceNumber + "\n\n" + body;
                if (!attachment.isEmpty()) {
                    String fileExtension = getFileExtension(selectedFile.getName());
                    String fileName = selectedFile.getName();
                    email += "\nATTACHMENT_NAME: " + fileName + "\nATTACHMENT: " + attachment;
                }

                saveEmailToFile(email, subject, timestamp);

                try {
                    byte[] sendData = email.getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                    clientSocket.send(sendPacket);
                    System.out.println("Mail Sent to Server, waiting...");

                    byte[] receiveData = new byte[BUFFER_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    clientSocket.receive(receivePacket);

                    String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    System.out.println(response);

                    // Send acknowledgment for the server's confirmation message
                    String acknowledgment = "ACK";
                    byte[] ackData = acknowledgment.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, serverAddress, SERVER_PORT);
                    clientSocket.send(ackPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                    // Handle the exception appropriately
                }

                System.out.print("Type 'new' to send another email, or 'quit' to exit: ");
                String decision = scanner.nextLine();
                if (decision.equalsIgnoreCase("quit")) {
                    try {
                        performTerminationSequence(clientSocket, serverAddress);
                    } catch (IOException e) {
                        e.printStackTrace();
                        // Handle the exception appropriately
                    }
                    break;
                }
                else if (!decision.equalsIgnoreCase("new"))
                {
                    System.out.println("Wrong input. Quitting.");
                    try {
                        performTerminationSequence(clientSocket, serverAddress);
                    } catch (IOException e) {
                        e.printStackTrace();
                        // Handle the exception appropriately
                    }
                    break;
                }
            }
        }
    }

    private static class ReceiverClient extends Thread {
        private final DatagramSocket clientSocket;
        private final InetAddress serverAddress;

        public ReceiverClient(DatagramSocket clientSocket, InetAddress serverAddress) {
            this.clientSocket = clientSocket;
            this.serverAddress = serverAddress;
        }

        public void run() {
 			if (clientSocket == null || clientSocket.isClosed()) {
        		System.err.println("Socket has not been initialized or was closed prematurely.");
        		return;
    		}
            System.out.println("Receiver Client is listening for incoming emails...");
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter your email address: ");
			
			String emailAddress = "";
			while (true) {
				try {
					emailAddress = scanner.nextLine();
					if (! emailAddress.equals("")) break;
				} catch (NoSuchElementException ex) {
					// ddd
				}
			}
            // Send the CONNECT_CLIENT message to the server
            String connectMessage = "CONNECT_CLIENT:" + emailAddress;
            byte[] connectData = connectMessage.getBytes();
            DatagramPacket connectPacket = new DatagramPacket(connectData, connectData.length, serverAddress, SERVER_PORT);
            try {
                clientSocket.send(connectPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Receive the response from the server
            byte[] responseData = new byte[BUFFER_SIZE];
            DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length);
            try {
                clientSocket.receive(responsePacket);
                String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
                if (response.equals("INVALID_EMAIL")) {
                    System.out.println("Invalid email address. Please restart the client and enter a valid email address.");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            while (true) {
                try {
                    // Check for new email notifications from the server
                    byte[] notificationData = new byte[BUFFER_SIZE];
                    DatagramPacket notificationPacket = new DatagramPacket(notificationData, notificationData.length);
                    clientSocket.setSoTimeout(1000); // Set a timeout of 1 second
                    try {
                        clientSocket.receive(notificationPacket);
                        String notificationMessage = new String(notificationPacket.getData(), 0, notificationPacket.getLength());
                        if (notificationMessage.startsWith("NEW_EMAIL:")) {
                            String emailFileName = notificationMessage.substring(10).trim();
                            System.out.println("New email received: " + emailFileName);
                            // performHandshake(clientSocket ,serverAddress);
                        }
                    } catch (SocketTimeoutException e) {
                        // No new email notifications received within the timeout period
                    }

                    System.out.println("Choose an action:");
                    System.out.println("1. View stored emails");
                    System.out.println("2. Quit");
                    System.out.print("Enter your choice (1 or 2): ");
                    String choiceInput = scanner.nextLine();

                    if (choiceInput.equals("1")) {
                        // Request the list of stored emails from the server for the specified email address
                        String request = "LIST_EMAILS:" + emailAddress;
                        byte[] sendData = request.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                        clientSocket.send(sendPacket);

                        // Receive the response from the server
                        byte[] receiveData = new byte[BUFFER_SIZE];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        clientSocket.receive(receivePacket);
                        String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

                        if (response.equals("INVALID_EMAIL")) {
                            System.out.println("Invalid email address. Please restart the client and enter a valid email address.");
                            break;
                        } else {
                            // Process the list of stored emails
                            if (response.equals("EMAIL_LIST_BEGIN")) {
                                List<String> emailList = new ArrayList<>();
                                while (true) {
                                    receiveData = new byte[BUFFER_SIZE];
                                    receivePacket = new DatagramPacket(receiveData, receiveData.length);
                                    clientSocket.receive(receivePacket);
                                    String emailResponse = new String(receivePacket.getData(), 0, receivePacket.getLength());

                                    if (emailResponse.startsWith("EMAIL_FILE:")) {
                                        String emailFile = emailResponse.substring(11).trim();
                                        emailList.add(emailFile);
                                    } else if (emailResponse.equals("EMAIL_LIST_END")) {
                                        break;
                                    }
                                } 
                                
                                if (!emailList.isEmpty()) {
                                    System.out.println("Stored Emails:");
                                    for (int i = 0; i < emailList.size(); i++) {
                                        System.out.println((i + 1) + ". " + emailList.get(i));
                                    }

                                    System.out.print("Enter the number of the email you want to view: ");
                                    String emailIndexInput = scanner.nextLine();
                                    int emailIndex;
                                    try {
                                        emailIndex = Integer.parseInt(emailIndexInput);
                                        if (emailIndex >= 1 && emailIndex <= emailList.size()) {
                                            String selectedEmail = emailList.get(emailIndex - 1);
                                            String getEmailRequest = "GET_EMAIL:" + emailAddress + ":" + selectedEmail;
                                            byte[] getEmailData = getEmailRequest.getBytes();
                                            DatagramPacket getEmailPacket = new DatagramPacket(getEmailData, getEmailData.length, serverAddress, SERVER_PORT);
                                            clientSocket.send(getEmailPacket);

                                            byte[] emailReceiveData = new byte[BUFFER_SIZE];
                                            DatagramPacket emailReceivePacket = new DatagramPacket(emailReceiveData, emailReceiveData.length);
                                            clientSocket.receive(emailReceivePacket);
                                            String emailContent = new String(emailReceivePacket.getData(), 0, emailReceivePacket.getLength());

                                            System.out.println("Email Content:");
                                            String[] emailLines = emailContent.split("\n");
                                            StringBuilder emailBodyBuilder = new StringBuilder();
                                            String attachmentName = "";
                                            String attachmentData = "";

                                            boolean isAttachment = false;
                                            for (String line : emailLines) {
                                                if (line.startsWith("ATTACHMENT_NAME: ")) {
                                                    attachmentName = line.substring(17).trim();
                                                    isAttachment = true;
                                                } else if (line.startsWith("ATTACHMENT: ")) {
                                                    attachmentData = line.substring(12).trim();
                                                } else {
                                                    if (!isAttachment) {
                                                        emailBodyBuilder.append(line).append("\n");
                                                    }
                                                }
                                            }

                                            String emailBody = emailBodyBuilder.toString().trim();
                                            System.out.println(emailBody);

                                            String clientDirectory = emailAddress + "/";
                                            String fileName = selectedEmail;
                                            Files.createDirectories(Paths.get(clientDirectory));
                                            Files.write(Paths.get(clientDirectory + fileName), emailBody.getBytes());

                                            if (!attachmentName.isEmpty() && !attachmentData.isEmpty()) {
                                                byte[] decodedAttachment = Base64.getDecoder().decode(attachmentData);

                                                String attachmentFileName = attachmentName;

                                                try {
                                                    System.out.println("Decoded attachment length: " + decodedAttachment.length);
                                                    Files.createDirectories(Paths.get(clientDirectory));
                                                    Files.write(Paths.get(clientDirectory + attachmentFileName), decodedAttachment);
                                                    System.out.println("Attachment saved to local directory: " + clientDirectory + attachmentFileName);
                                                } catch (IOException e) {
                                                    System.out.println("Failed to save attachment: " + e.getMessage());
                                                    // Handle the exception appropriately, e.g., log the error, notify the user, etc.
                                                }
                                            }

                                            System.out.println("Email saved to local directory: " + clientDirectory);
                                        } else {
                                            System.out.println("Invalid email selection.");
                                        }
                                    } catch (NumberFormatException e) {
                                        System.out.println("Invalid email selection.");
                                    }
                                } else {
                                    System.out.println("No stored emails found.");
                                }
                            } else if (response.equals("NO_EMAILS")) {
                                System.out.println("No stored emails found.");
                            }
                        }
                    } else if (choiceInput.equals("2")) {
                        try {
                            performTerminationSequence(clientSocket, serverAddress);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    } else {
                        System.out.println("Invalid choice. Please try again.");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            String disconnectMessage = "DISCONNECT_CLIENT:" + emailAddress;
            byte[] disconnectData = disconnectMessage.getBytes();
            DatagramPacket disconnectPacket = new DatagramPacket(disconnectData, disconnectData.length, serverAddress, SERVER_PORT);
            try {
                clientSocket.send(disconnectPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private static void performTerminationSequence(DatagramSocket clientSocket, InetAddress serverAddress) throws IOException {
        String fin = "FIN";
        byte[] sendData = fin.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
        clientSocket.send(sendPacket);

        byte[] receiveData = new byte[BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(receivePacket);
        String ack = new String(receivePacket.getData(), 0, receivePacket.getLength());

        if (ack.equals("ACK")) {
            System.out.println("Connection terminated with the server.");
        } else {
            System.out.println("Termination sequence failed.");
        }
    }

    private static void saveEmailToFile(String email, String subject, String timestamp) {
        String fileName = subject + "_" + timestamp + ".txt";
        fileName = fileName.replaceAll("[:\\\\/*?|<>]", "_"); // Replace invalid characters with '_'
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(email);
            System.out.println("Email saved to file: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    private static String encodeFileToBase64(File file) {
        try {
            byte[] fileContent = Files.readAllBytes(file.toPath());
            return Base64.getEncoder().encodeToString(fileContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
}
