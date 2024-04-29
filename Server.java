import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.net.*;

public class Server{
    private static List<Client> connectedClients = new ArrayList<>();//dynamic array simple methods
    private static Map<String, List<Email>> pendingEmails = new ConcurrentHashMap<>();

    private static int senderNum = 1;
    private static int receiverNum = 1;
    private static byte[] receiveData = new byte[1024];

    public static void main(String[] args) throws FileNotFoundException{
        DatagramSocket serverSocket = null;

        try {
            //local variables
            serverSocket = new DatagramSocket(12121);
            
            String[] vaildTOEmails = {"zayed@gmail.com", "zaid@gmail.com", "zaza@gmail.com"};

            InetAddress clientAddress = null;
            int clientPort = 0;
            String clientEmail = "";

            int toIndex = -1;
            int fromIndex = -1;

            InetAddress IP = InetAddress.getLocalHost();
            System.out.println("Mail Server Starting at host: "+ IP.getHostName());
            System.out.println("Server is listening on port 12121...");

            while (true) {
                printAllEmails();
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length); 

                serverSocket.receive(receivePacket); 
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                String temp[];

                if (message.contains("SYN"))
                {
                    System.out.println("SYN received");
                    System.out.println("Sending ACK");
                    temp = message.split("SYN:");
                    System.out.println("Client Email: " + temp[1] + ".");
                    clientEmail = temp[1].trim();
                    clientAddress = receivePacket.getAddress();
                    clientPort = receivePacket.getPort();
                    Client ctemp = new Client(temp[1], clientAddress, clientPort);
                    connectedClients.add(ctemp);
                    send_message("ACK", clientAddress, clientPort, serverSocket);
                    serverSocket.receive(receivePacket);
                    String ackack = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    if (ackack.contains("ACK ACK"))
                    {
                        System.out.println("ACK ACK received");
                    }
                    List<Email> emailsToDeliver = pendingEmails.getOrDefault(clientEmail, new ArrayList<>());
                    for (Email email : emailsToDeliver) {
                        // Reconstruct the message format or directly use the stored email objects
                        sendEmailToClient(email, clientAddress, clientPort, serverSocket);
                    }
                    pendingEmails.remove(clientEmail);
                }

                if (message.contains("TERMINATE:"))
                {
                    temp = message.split("TERMINATE:");
                    System.out.println("Terminate received from: " + temp[1]);
                    System.out.println("Sending ACK");
                    clientAddress = receivePacket.getAddress();
                    clientPort = receivePacket.getPort();
                    send_message("TERMINATE:ACK", clientAddress, clientPort, serverSocket);
                    serverSocket.receive(receivePacket);
                    String termString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    if (termString.equals("ACK ACK") && removeClientByEmail(temp[1]))
                    {
                        System.out.println("ACK ACK received");
                        System.out.println("Terminating");
                    }
                }

                if (message.contains("TO:"))
                {
                    System.out.println("Email received");
                    String a1[] = message.split("TO:");
                    String a2[] = a1[1].split("FROM:");
                    String to = a2[0];
                    String a3[] = a2[1].split("SUBJECT:");
                    String from = a3[0];
                    String a4[] = a3[1].split("SEQ:");
                    String subject = a4[0];
                    String a5[] = a4[1].split("BODY:");
                    senderNum = Integer.parseInt(a5[0]);
                    String a6[] = a5[1].split("ATTACHMENT:");
                    String body = a6[0];
                    String a7[] = a6[1].split("HOST:");
                    String attachmentData = a7[0];
                    String hostname = a7[1];
                    String timestamp = java.time.LocalDateTime.now().toString().replace(":", "-");

                    boolean found = false;
                    for (String s: vaildTOEmails)
                    {
                        if (s.equalsIgnoreCase(to))
                        {
                            found = true;
                            break ;
                        }
                    }

                    if (!to.contains("@") || !from.contains("@")){
                        System.out.println("The Header fields are not valid.");
                        System.out.println("Sending 501 Error");
    
                        String confirmation = "501 ERROR";
                        clientAddress = receivePacket.getAddress();
                        clientPort = receivePacket.getPort();
                        send_message(confirmation, clientAddress, clientPort, serverSocket);
                        serverSocket.receive(receivePacket);
                        
                        String ackString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        if (ackString.contains("ACK"))
                        {
                            System.out.println("ACK received");
                        }
                        else
                        {
                            System.out.println("ACK error");
                        }
                        continue ;
                    }//505 error
                    else if (!found)
                    {
                        System.out.println("Email address does not exist");
                        System.out.println("Sending 505 Error");
    
                        String confirmation = "505 ERROR";
                        clientAddress = receivePacket.getAddress();
                        clientPort = receivePacket.getPort();
                        send_message(confirmation, clientAddress, clientPort, serverSocket);
                        String ackString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        if (ackString.contains("ACK"))
                        {
                            System.out.println("ACK received");
                        }
                        else
                        {
                            System.out.println("ACK error");
                        }
                        continue ;
                    }

                    fromIndex = getClientIndex(from);
                    toIndex = getClientIndex(to);

                    System.out.println("(Sender) Sending ACK:" + (senderNum + receivePacket.getLength()));
                    send_message("SENDERACK:" + (senderNum + receivePacket.getLength()), 
                        connectedClients.get(fromIndex).getAddress(), connectedClients.get(fromIndex).getPort(), serverSocket);

                    System.out.println("Mail Received from " + hostname); 
                    System.out.println("FROM: " + from);
                    System.out.println("TO: " + to);
                    System.out.println("SUBJECT: " + subject);
                    System.out.println("TIME: " + timestamp);
                    System.out.println("SEQ: " + senderNum);
                    System.out.println(body);
                    
                    
                    String directoryPath = "./" + to + "ServerMails/";
                    String filename = subject + "_" + timestamp;
                    String relativeFilePath = directoryPath + filename;
                    //create directory of the client if valid
                    if (found)
                    {
                        File directory = new File(directoryPath);
                        directory.mkdirs();
                    }

                    if (toIndex == -1)
                    {
                        System.out.println(to + " is not connected. Email will be stored for later delivery.");
                        pendingEmails.putIfAbsent(to, new ArrayList<>());
                        pendingEmails.get(to).add(new Email(to, from, subject, body, attachmentData, timestamp));
                        System.out.println("Sending 200 OK");
                        send_message("200 OK:" + timestamp, 
                            connectedClients.get(fromIndex).getAddress(), connectedClients.get(fromIndex).getPort(), serverSocket);
                        serverSocket.receive(receivePacket);
                        String ackString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        if (ackString.contains("ACK"))
                        {
                            System.out.println("ACK:" + (senderNum + receivePacket.getLength()) + " received");
                        }
                        File f = new File(relativeFilePath + ".txt");
    
                        PrintWriter fout = new PrintWriter(f);
                        fout.println("FROM: " + from);
                        fout.println("TO: " + to);
                        fout.println("SUBJECT: " + subject);
                        fout.println("TIME: " + timestamp);
                        fout.println(body);
                        fout.close();
                        //add attachment to that directory
                        if (!attachmentData.isEmpty()) {
                            String splitter[] = attachmentData.split(",");
                            String attachmentDecode = splitter[0];
                            String attachmentExtension = splitter[1];
                            byte[] decodedBytes = Base64.getDecoder().decode(attachmentDecode);
    
                            File attachmentFile = new File(relativeFilePath + "_attach." + attachmentExtension);
                            try (FileOutputStream fos = new FileOutputStream(attachmentFile)) {
                                fos.write(decodedBytes);
                                System.out.println("Attachment saved to " + directoryPath);
                            } catch (IOException e) {
                                System.out.println("Error saving attachment: " + e.getMessage());
                            }
                        }
                        printPendingEmails();
                        continue ;
                    }
    
                    //250 ok
                    if (to.contains("@") && from.contains("@") && found )
                    {
                        System.out.println("The Header fields are verified.");
                        System.out.println("Sending mail to client receiver: " + connectedClients.get(toIndex).getMail() + " index: " + toIndex);
                        //send mail to receiver if same "to" mail
                        String receiverMessage = "TO:" + to + "FROM:" + from + "SUBJECT:" + subject + "SEQ:" + receiverNum + "BODY:" + body + "ATTACHMENT:" + attachmentData + "HOST:" + hostname;
                        
                        send_message(receiverMessage, connectedClients.get(toIndex).getAddress(), 
                            connectedClients.get(toIndex).getPort(), serverSocket);
                        System.out.println("mail sent to " + connectedClients.get(toIndex).getAddress() + " at: " + connectedClients.get(toIndex).getPort());
                        serverSocket.receive(receivePacket);
                        String ackString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        if (ackString.contains("ACK"))
                        {
                            String tempStr[] = ackString.split("ACK:");
                            receiverNum = Integer.parseInt(tempStr[1]);
                            System.out.println("(Receiver) ACK:" + receiverNum + " received");
                        }
                        else
                            System.out.println("ACK error");
                        
                        
                        System.out.println("Sending 250 Ok");
                        String confirmation = "250 OK:" + timestamp;
                        //250 ok to sender
                        
                        send_message(confirmation, connectedClients.get(fromIndex).getAddress(), connectedClients.get(fromIndex).getPort(), serverSocket);
                        serverSocket.receive(receivePacket);
                        ackString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        if (ackString.contains("ACK"))
                        {
                            System.out.println("ACK received");
                        }
                        else
                        {
                            System.out.println("ACK error");
                        }
                        //print mail to directory
                        File f = new File(relativeFilePath + ".txt");
    
                        PrintWriter fout = new PrintWriter(f);
                        fout.println("FROM: " + from);
                        fout.println("TO: " + to);
                        fout.println("SUBJECT: " + subject);
                        fout.println("TIME: " + timestamp);
                        fout.println(body);
                        fout.close();
                        //add attachment to that directory
                        if (!attachmentData.isEmpty()) {
                            String splitter[] = attachmentData.split(",");
                            String attachmentDecode = splitter[0];
                            String attachmentExtension = splitter[1];
                            byte[] decodedBytes = Base64.getDecoder().decode(attachmentDecode);
    
                            File attachmentFile = new File(relativeFilePath + "_attach." + attachmentExtension);
                            try (FileOutputStream fos = new FileOutputStream(attachmentFile)) {
                                fos.write(decodedBytes);
                                System.out.println("Attachment saved to " + directoryPath);
                            } catch (IOException e) {
                                System.out.println("Error saving attachment: " + e.getMessage());
                            }
                        }
                        System.out.println("mail sent to client at " + connectedClients.get(toIndex).getMail());
                    }//501 error
                    
                    toIndex = -1;
                    fromIndex = -1;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        }
    }

    static void send_message(String message, InetAddress serverAddress, int portNumber, DatagramSocket clientSocket)
    {
        try{
            byte[] sendData = message.getBytes(); //convert message to bytes
            int messageBytes = message.getBytes().length; //byte length of request message
            String byteSize = Integer.toString(messageBytes); //parsing int to string
            System.out.println("Message is sending " + byteSize + " Bytes"); //print bytes length
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, portNumber); //create a packet
            clientSocket.send(sendPacket); //send packet(Bytes, Bytes length, address to send, port number)
        } catch(IOException e) { //catch IOException
            e.printStackTrace();
        }
    }

    static int getClientIndex(String clientMail)
    {
        for (int i = 0; i < connectedClients.size(); i++) {
            if (connectedClients.get(i).getMail().equals(clientMail)) {
                return i;  // Return the index if the mail matches
            }
        }
        // If no client with the specified email is found, handle accordingly
        System.out.println("Client with email " + clientMail + " is not connected.");
        return -1;
    }

    public static void printAllEmails() {
        if (connectedClients.isEmpty()) {
            System.out.println("No clients are currently connected.");
        } else {
            System.out.println("Listing all connected client emails:");
            for (Client client : connectedClients) {
                System.out.print(client.getMail() + " ");
            }
            System.out.println();
        }
    }

    private static boolean removeClientByEmail(String email) {
        return connectedClients.removeIf(client -> client.getMail().equals(email));
    }

    static void sendEmailToClient(Email email, InetAddress serverAddress, int portNumber, DatagramSocket clientSocket) throws IOException{
        String receiverMessage = "TO:" + email.getTO() + "FROM:" + email.getFROM() + "SUBJECT:" + email.getSubject() + "SEQ:" + receiverNum + "BODY:" + email.getBody() + "ATTACHMENT:" + email.getAttachmentData() + "HOST:" + email.getTO();
        
        send_message(receiverMessage, serverAddress, portNumber, clientSocket);
        System.out.println("mail sent to " + serverAddress + " at: " + portNumber);
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(receivePacket);
        String ackString = new String(receivePacket.getData(), 0, receivePacket.getLength());
        if (ackString.contains("ACK"))
        {
            String tempStr[] = ackString.split("ACK:");
            receiverNum = Integer.parseInt(tempStr[1]);
            System.out.println("(Receiver) ACK:" + receiverNum + " received");
        }
        else
            System.out.println("ACK error");
    }

    public static void printPendingEmails() {
        if (pendingEmails.isEmpty()) {
            System.out.println("No pending emails.");
        } else {
            System.out.println("Pending emails:");
            for (Map.Entry<String, List<Email>> entry : pendingEmails.entrySet()) {
                String recipient = entry.getKey();
                List<Email> emails = entry.getValue();
                System.out.println("Recipient: " + recipient);
                for (Email email : emails) {
                    System.out.println("\tFrom: " + email.getFROM());
                    System.out.println("\tTo: " + email.getTO());
                    System.out.println("\tSubject: " + email.getSubject());
                    System.out.println("\tBody: " + email.getBody());
                    System.out.println("\tAttachment Data: " + email.getAttachmentData());
                    System.out.println("\tTimestamp: " + email.getTimestamp());
                    System.out.println("\t---");
                }
            }
        }
    }
}

class Client
{
    private String mail;
    private InetAddress address;
    private int port;

    public Client(String mail, InetAddress address, int port)
    {
        this.mail = mail;
        this.address = address;
        this.port = port;
    }

    public String getMail() {
        return mail;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }
}


class Email {
    String TO;
    String FROM;
    String subject;
    String body;
    String attachmentData; // Simplified: actual implementation might need more
    String timestamp;

    public Email(String TO, String FROM, String subject, String body, String attachmentData, String timestamp)
    {
        this.TO = TO;
        this.FROM = FROM;
        this.subject = subject;
        this.body = body;
        this.attachmentData = attachmentData;
        this.timestamp = timestamp;
    }
    public String getTO() {return TO;}
    public String getFROM() {return FROM;}
    public String getSubject() {return subject;}
    public String getBody() {return body;}
    public String getAttachmentData() {return attachmentData;}
    public String getTimestamp() {return timestamp;}
}