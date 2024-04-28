import java.io.*;
import java.util.*;
import java.net.*;

public class Server{
    private static List<Client> connectedClients = new ArrayList<>();//dynamic array simple methods

    public static void main(String[] args) throws FileNotFoundException{
        DatagramSocket serverSocket = null;

        try {
            //local variables
            serverSocket = new DatagramSocket(12121);
            byte[] receiveData = new byte[1024];
            String[] vaildTOEmails = {"zayed@gmail.com", "zaid@gmail.com", "zaza@gmail.com"};
            String receiverMail = "";

            InetAddress clientAddress = null;
            int clientPort = 0;

            int senderNum = 1;
            int receiverNum = 1;

            int tempIndex = 0;
            int toIndex = -1;
            int fromIndex = -1;

            InetAddress IP = InetAddress.getLocalHost();
            System.out.println("Mail Server Starting at host: "+ IP.getHostName());
            System.out.println("Server is listening on port 12121...");

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length); 

                serverSocket.receive(receivePacket); 
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                String temp[];

                if (message.contains("SYN"))
                {
                    System.out.println("SYN received");
                    System.out.println("Sending ACK");
                    temp = message.split("SYN:");
                    System.out.println("Client Email: " + temp[1]);
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
                }

                if (message.contains("TERMINATE:"))
                {
                    temp = message.split("TERMINATE:");
                    System.out.println("Terminate received from: " + temp[1]);
                    System.out.println("Sending ACK");
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

                    fromIndex = getClientIndex(from);
                    toIndex = getClientIndex(to);

                    System.out.println("(Sender) Sending ACK:" + (senderNum + receivePacket.getLength()));
                    send_message("SENDERACK:" + (senderNum + receivePacket.getLength()), 
                        connectedClients.get(fromIndex).getAddress(), connectedClients.get(fromIndex).getPort(), serverSocket);

                    System.out.println("Mail Received from " + hostname); 
                    String timestamp = java.time.LocalDateTime.now().toString().replace(":", "-");

                    System.out.println("FROM: " + from);
                    System.out.println("TO: " + to);
                    System.out.println("SUBJECT: " + subject);
                    System.out.println("TIME: " + timestamp);
                    System.out.println("SEQ: " + senderNum);
                    System.out.println(body);

                    boolean found = false;
                    for (String s: vaildTOEmails)
                    {
                        if (s.equalsIgnoreCase(to))
                        {
                            found = true;
                            break ;
                        }
                    }
                    
                    receiverMail = connectedClients.get(toIndex).getMail();
                    String directoryPath = "./" + to + "ServerMails/";
                    String filename = subject + "_" + timestamp;
                    String relativeFilePath = directoryPath + filename;
                    //create directory of the client if valid
                    if (found || to.equalsIgnoreCase(receiverMail))
                    {
                        File directory = new File(directoryPath);
                        directory.mkdirs();
                    }
    
                    //250 ok
                    if (to.contains("@") && from.contains("@") && (found || to.equalsIgnoreCase(receiverMail)))
                    {
                        System.out.println("The Header fields are verified.");
                        System.out.println("Sending mail to client receiver: " + connectedClients.get(toIndex).getMail() + " index: " + toIndex);
                        //send mail to receiver if same "to" mail
                        if (to.equalsIgnoreCase(receiverMail))
                        {

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
                        }
                        
                        System.out.println("Sending 250 Ok");
                        String confirmation = "250 OK:" + timestamp;
                        //250 ok to sender
                        
                        send_message(confirmation, connectedClients.get(fromIndex).getAddress(), connectedClients.get(fromIndex).getPort(), serverSocket);
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
                        System.out.println("mail sent to client at " + hostname + ":" + clientAddress);
                    }//501 error
                    else if (!to.contains("@") || !from.contains("@")){
                        System.out.println("The Header fields are not valid.");
                        System.out.println("Sending 501 Error");
    
                        String confirmation = "501 Error";
    
                        send_message(confirmation, connectedClients.get(fromIndex).getAddress(), connectedClients.get(fromIndex).getPort(), serverSocket);
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
                    }//505 error
                    else if (!to.equals(receiverMail) || !found)
                    {
                        System.out.println("Email address does not exist");
                        System.out.println("Sending 505 Error");
    
                        String confirmation = "505 Error";
    
                        send_message(confirmation, connectedClients.get(fromIndex).getAddress(), connectedClients.get(fromIndex).getPort(), serverSocket);
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
                    }
                    toIndex = -1;
                    fromIndex = -1;
                }
                printAllEmails();
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