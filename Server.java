import java.io.*;
import java.net.*;
import java.util.Base64;

public class Server{
    public static void main(String[] args) throws FileNotFoundException{
        DatagramSocket serverSocket = null;

        try {
            //local variables
            serverSocket = new DatagramSocket(12121);
            byte[] receiveData = new byte[1024];
            String[] vaildTOEmails = {"zayed@gmail.com", "zaid@gmail.com"};
            String receiverMail = "";

            boolean senderActive = false;
            boolean receiverActive = false;

            InetAddress senderAddress = null;
            InetAddress receiverAddress = null;
            int senderPort = 0;
            int receiverPort = 0;

            int senderNum = 1;
            int receiverNum = 1;

            InetAddress IP = InetAddress.getLocalHost();
            System.out.println("Mail Server Starting at host: "+ IP.getHostName()); 
            System.out.println("Server is listening on port 12121...");

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length); 

                serverSocket.receive(receivePacket);
                InetAddress clientAddress = receivePacket.getAddress(); 
                
                //waits for both clients to be active
                while (!senderActive || !receiverActive)
                {
                    //3 way handshake for both clients
                    String syn = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    if (syn.contains("SYN-Sender") && !senderActive)
                    {
                        
                        System.out.println("SYN received");
                        System.out.println("Sending ACK");
                        senderAddress = receivePacket.getAddress();
                        senderPort = receivePacket.getPort();
                        System.out.println("sender port: " + senderPort);
                        send_message("ACK", senderAddress, senderPort, serverSocket);
                        serverSocket.receive(receivePacket);
                        String ackack = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        if (ackack.contains("ACK ACK"))
                        {
                            System.out.println("ACK ACK received");
                            senderActive = true;
                        }
                    }
                    else if (syn.contains("SYN-Receiver") && !receiverActive)
                    {
                        System.out.println("SYN received");
                        System.out.println("Sending ACK");
                        receiverAddress = receivePacket.getAddress();
                        receiverPort = receivePacket.getPort();
                        String s1[] = syn.split("SYN-Receiver");
                        receiverMail = s1[1];
                        System.out.println("receiver port: " + receiverPort);
                        System.out.println("Client receiver mail: " + receiverMail);
                        send_message("ACK", receiverAddress, receiverPort, serverSocket);
                        serverSocket.receive(receivePacket);
                        String ackack = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        if (ackack.contains("ACK ACK"))
                        {
                            System.out.println("ACK ACK received");
                            receiverActive = true;
                        }
                    }
                    serverSocket.receive(receivePacket);
                }
                
                //termination sequence
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                if (message.equals("Terminate"))
                {
                    System.out.println("Terminate received");
                    System.out.println("Sending ACK");
                    send_message("ACK", senderAddress, senderPort, serverSocket);
                    serverSocket.receive(receivePacket);
                    String termString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    if (termString.equals("ACK ACK"))
                    {
                        System.out.println("ACK ACK received");
                        System.out.println("Terminating");
                        senderActive = false;
                        continue ;
                    }
                }

                //splitting message contents into variables
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

                //sending ack to sender
                System.out.println("(Sender) Sending ACK:" + (senderNum + receivePacket.getLength()));
                send_message("ACK:" + (senderNum + receivePacket.getLength()), senderAddress, senderPort, serverSocket);
                System.out.println("Mail Received from " + hostname); 
                String timestamp = java.time.LocalDateTime.now().toString().replace(":", "-");
                
                //printing contents of email
                System.out.println("FROM: " + from);
                System.out.println("TO: " + to);
                System.out.println("SUBJECT: " + subject);
                System.out.println("TIME: " + timestamp);
                System.out.println("SEQ: " + senderNum);
                System.out.println(body);
                
                //look for available email
                boolean found = false;
                for (String s: vaildTOEmails)
                {
                    if (s.equalsIgnoreCase(to))
                    {
                        found = true;
                        break ;
                    }
                }


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
                    System.out.println("Sending mail to client receiver...");
                    //send mail to receiver if same "to" mail
                    if (to.equalsIgnoreCase(receiverMail))
                    {
                        String receiverMessage = "TO:" + to + "FROM:" + from + "SUBJECT:" + subject + "SEQ:" + receiverNum + "BODY:" + body + "ATTACHMENT:" + attachmentData + "HOST:" + hostname;
                        send_message(receiverMessage, receiverAddress, receiverPort, serverSocket);
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
                    
                    send_message(confirmation, senderAddress, senderPort, serverSocket);
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

                    send_message(confirmation, senderAddress, senderPort, serverSocket);
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

                    send_message(confirmation, senderAddress, senderPort, serverSocket);
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
}
