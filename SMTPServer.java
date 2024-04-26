import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

public class SMTPServer {
    private static final int PORT = 12345;
    private static Map<String, String> emailToHostMapping = new HashMap<>();
    private static Map<String, List<InetSocketAddress>> connectedReceiverClients = new HashMap<>();
    
    static {
        // Pre-defined email to host mapping for demo purposes
        emailToHostMapping.put("client1@example.com", "client1");
        emailToHostMapping.put("client2@example.com", "client2");
    }

    public static void main(String[] args) {
        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {
            System.out.println("Mail Server Starting at host: " + InetAddress.getLocalHost().getHostName());
            System.out.println("Waiting to be contacted for transferring Mail...");

            while (true) {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);
                String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());

                if (receivedMessage.equals("SYN")) {
                    // Respond to the SYN packet with SYN-ACK
                    String synAck = "SYN-ACK";
                    byte[] sendData = synAck.getBytes();
                    InetAddress clientAddress = receivePacket.getAddress();
                    int clientPort = receivePacket.getPort();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                    serverSocket.send(sendPacket);
                    System.out.println("Sent SYN-ACK to " + clientAddress.getHostName());
                } else if (receivedMessage.equals("FIN")) {
                    // Respond to the FIN packet with ACK
                    String ack = "ACK";
                    byte[] sendData = ack.getBytes();
                    InetAddress clientAddress = receivePacket.getAddress();
                    int clientPort = receivePacket.getPort();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                    serverSocket.send(sendPacket);
                    System.out.println("Sent ACK to " + clientAddress.getHostName());
                } else if (receivedMessage.equals("ACK")) {
                    // Ignore the ACK message sent by the client
                    System.out.println("Received ACK from " + receivePacket.getAddress().getHostName());
                } else if (receivedMessage.startsWith("CONNECT_CLIENT:")) {
                    String emailAddress = receivedMessage.substring(15).trim();
                    if (isValidEmailAddress(emailAddress)) {
                        InetSocketAddress clientAddress = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
                        connectedReceiverClients.computeIfAbsent(emailAddress, k -> new ArrayList<>()).add(clientAddress);
                        System.out.println("Connected receiver client: " + emailAddress);
                    } else {
                        System.out.println("Invalid email address: " + emailAddress);
                    }
                } else if (receivedMessage.startsWith("DISCONNECT_CLIENT:")) {
                    String emailAddress = receivedMessage.substring(18).trim();
                    InetSocketAddress clientAddress = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
                    connectedReceiverClients.getOrDefault(emailAddress, new ArrayList<>()).remove(clientAddress);
                    System.out.println("Disconnected receiver client: " + emailAddress);
                } else if (receivedMessage.startsWith("LIST_EMAILS:")) {
                    String emailAddress = receivedMessage.substring(12).trim();
                    String clientDirectory = emailAddress + "/";
                    File directory = new File(clientDirectory);
                    if (directory.exists()) {	
                        String[] emailFiles = directory.list((dir, name) -> name.endsWith(".txt"));
                        if (emailFiles != null && emailFiles.length > 0) {
                            String response = "EMAIL_LIST_BEGIN";
                            byte[] responseData = response.getBytes();
                            InetAddress clientAddress = receivePacket.getAddress();
                            int clientPort = receivePacket.getPort();
                            DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                            serverSocket.send(sendPacket);
                
                            for (String emailFile : emailFiles) {
                                response = "EMAIL_FILE:" + emailFile;
                                responseData = response.getBytes();
                                sendPacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                                serverSocket.send(sendPacket);
                            }
                
                            response = "EMAIL_LIST_END";
                            responseData = response.getBytes();
                            sendPacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                            serverSocket.send(sendPacket);
                        } else {
                            String response = "NO_EMAILS";
                            byte[] responseData = response.getBytes();
                            InetAddress clientAddress = receivePacket.getAddress();
                            int clientPort = receivePacket.getPort();
                            DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                            serverSocket.send(sendPacket);
                        }
                    } else {
                        String response = "NO_EMAILS";
                        byte[] responseData = response.getBytes();
                        InetAddress clientAddress = receivePacket.getAddress();
                        int clientPort = receivePacket.getPort();
                        DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                        serverSocket.send(sendPacket);
                    }
                } else if (receivedMessage.startsWith("GET_EMAIL:")) {
                    String[] parts = receivedMessage.substring(10).split(":");
                    String emailAddress = parts[0].trim();
                    String selectedEmail = parts[1].trim();
                
                    String clientDirectory = emailAddress + "/";
                    String emailFilePath = clientDirectory + selectedEmail;
                    File emailFile = new File(emailFilePath);
                
                    if (emailFile.exists()) {
                        byte[] emailData = Files.readAllBytes(emailFile.toPath());
                        InetAddress clientAddress = receivePacket.getAddress();
                        int clientPort = receivePacket.getPort();
                        DatagramPacket sendPacket = new DatagramPacket(emailData, emailData.length, clientAddress, clientPort);
                        serverSocket.send(sendPacket);
                    } else {
                        String response = "EMAIL_NOT_FOUND";
                        byte[] responseData = response.getBytes();
                        InetAddress clientAddress = receivePacket.getAddress();
                        int clientPort = receivePacket.getPort();
                        DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                        serverSocket.send(sendPacket);
                    }
                } else {
                    // Process the received email
                    String receivedEmail = receivedMessage;
                    System.out.println("Mail Received from " + receivePacket.getAddress().getHostName());
                    System.out.println(receivedEmail);
                
                    String response;
                    if (isEmailValid(receivedEmail)) {
                        String fromEmail = extractEmailHeader(receivedEmail, "FROM:");
                        String toEmail = extractEmailHeader(receivedEmail, "TO:");
                        
                        // Validate the "FROM" and "TO" email addresses
                        if (!isValidEmailAddress(fromEmail) || !isValidEmailAddress(toEmail)) {
                            response = "550 Invalid email address in 'FROM' or 'TO' field";
                            System.out.println(response);
                            
                            byte[] responseData = response.getBytes();
                            InetAddress clientAddress = receivePacket.getAddress();
                            int clientPort = receivePacket.getPort();
                            DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                            serverSocket.send(sendPacket);
                            continue;
                        }
                        
                        String subject = extractEmailHeader(receivedEmail, "SUBJECT:");
                        String timestamp = extractEmailHeader(receivedEmail, "TIME-STAMP:");
                        String sequenceNumber = extractEmailHeader(receivedEmail, "SEQUENCE-NUMBER:");
                
                        String clientDirectory = toEmail + "/";
                        String fileName = subject + "_" + timestamp + ".txt";
                        fileName = fileName.replaceAll("[:\\\\/*?|<>]", "_");
                        Files.createDirectories(Paths.get(clientDirectory));
                        Files.write(Paths.get(clientDirectory + fileName), receivedEmail.getBytes());
                
                        response = "250 OK\n" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy HH:mm:ss"));
                
                        // Check if there are any connected receiver clients for the recipient email address
                        List<InetSocketAddress> receiverClients = connectedReceiverClients.get(toEmail);
                        if (receiverClients != null) {
                            String notificationMessage = "NEW_EMAIL:" + fileName;
                            byte[] notificationData = notificationMessage.getBytes();
                            for (InetSocketAddress receiverClient : receiverClients) {
                                DatagramPacket notificationPacket = new DatagramPacket(notificationData, notificationData.length, receiverClient.getAddress(), receiverClient.getPort());
                                serverSocket.send(notificationPacket);
                            }
                        }
                    } else {
                        response = "501 Syntax error in parameters or arguments";
                    }
                
                    System.out.println(response);
                
                    byte[] responseData = response.getBytes();
                    InetAddress clientAddress = receivePacket.getAddress();
                    int clientPort = receivePacket.getPort();
                    DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                    serverSocket.send(sendPacket);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isValidEmailAddress(String emailAddress) {
        // Check if the email address is present in the emailToHostMapping map
        return emailToHostMapping.containsKey(emailAddress);
    }

    private static boolean isEmailValid(String emailData) {
        return emailData.contains("FROM:") && emailData.contains("TO:") && emailData.contains("SUBJECT:")
                && emailData.contains("TIME-STAMP:") && emailData.contains("SEQUENCE-NUMBER:")
                && (!emailData.contains("ATTACHMENT:") || emailData.contains("ATTACHMENT_NAME:"));
    }

    private static String extractEmailHeader(String emailData, String header) {
        int startIndex = emailData.indexOf(header);
        if (startIndex == -1) {
            // Header not found, handle as needed (returning an empty string or error message here)
            return "Header not found";
        }
        startIndex += header.length();
        
        int endIndex = emailData.indexOf("\n", startIndex);
        if (endIndex == -1) {
            // Newline not found after the header, handle as needed
            // For safety, you could return the rest of the string or a specific error message
            return emailData.substring(startIndex).trim();
        }
        
        return emailData.substring(startIndex, endIndex).trim();
    }
}