import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.file.Files;

public class ClientSender{ //Client (Email writter)
    public static int sequenceNum = 1;
    public static String hostMail, to, from, subject, body, attachFile, attachmentBase64 = "";

    public static void main(String[] args) {
        final DatagramSocket[] clientSocketWrapper = new DatagramSocket[1]; //create an empty socket
        final InetAddress[] serverAddress = new InetAddress[1];
        Scanner console = new Scanner(System.in); //for user input
        int serverPort = 12121;
        // int sequenceNum = 1;

        try { //error handler to catch io errors
            InetAddress IP = InetAddress.getLocalHost();
            String hostname = IP.getHostName();
            System.out.println("Mail Client Starting at host: "+ hostname); //prints the hostname (DESKTOP-XXXX)
            hostMail = "";
            while(true)
            {
                System.out.print("Enter Your Email: ");
                hostMail = console.nextLine();
                if (hostMail.contains("@") && hostMail.contains(".com"))
                {
                    break ;
                }
                System.out.println("Invalid Email");
            }
            serverAddress[0] = null;
            while (true) {
                try {
                    System.out.print("Type name of Mail servers: ");
                    String mailServer = console.nextLine();
                    serverAddress[0] = InetAddress.getByName(mailServer);
                    break ; 
                } catch (UnknownHostException ex) { 
                    System.out.println("Unknown host name");
                }
            }
            clientSocketWrapper[0] = new DatagramSocket(); //create empty socket object
            handshake(clientSocketWrapper[0], serverAddress[0], serverPort);

            Thread receiverThread = new Thread(() -> {
                try{
                    receiveEmail(serverAddress[0], serverPort, clientSocketWrapper[0]);
                } catch (IOException e) {
                    System.out.println("Thread error" + e.getMessage());
                }
            });
            receiverThread.start();

            while(true) //infinite loop until break
            {
                System.out.println("Creating New Email.."); //mail inputs
                System.out.print("To: ");
                to = console.nextLine();
                System.out.println("From: " + hostMail);
                from = hostMail;
                System.out.print("Subject: ");
                subject = console.nextLine();
                System.out.print("Body: ");
                body = console.nextLine();

                while(true)
                {
                    System.out.print("Attach a file? (yes/no): ");
                    attachFile = console.nextLine();
                    if (attachFile.equalsIgnoreCase("yes")) {
                        System.out.print("Enter file path: ");
                        String filePath = console.nextLine();
                        File file = new File(filePath);
                        if (file.exists() && !file.isDirectory()) {
                            byte[] fileContent = Files.readAllBytes(file.toPath());
                            String fileExtension = getFileExtension(file);
                            attachmentBase64 = Base64.getEncoder().encodeToString(fileContent) + "," + fileExtension; // Append file extension
                            break ;
                        } else {
                            System.out.println("File does not exist or is a directory.");
                            continue ;
                        }
                    }
                    else if (attachFile.equalsIgnoreCase("no"))
                    {
                        break ;
                    }
                    else
                    {
                        System.out.println("Invalid Input");
                        continue ;
                    }
                }

                String request = "TO:" + to + "FROM:" + from + "SUBJECT:" + subject + "SEQ:" + sequenceNum + "BODY:" + body + "ATTACHMENT:" + attachmentBase64 + "HOST:" + hostname;

                if (request.getBytes().length > 1024) {
                    System.out.println("The total email size exceeds the 1024 bytes limit. Consider reducing the attachment size.");
                    continue; // Skip sending this message
                }

                //String request = "TO:" + to + "FROM:" + from + "SUBJECT:" + subject + "SEQ:" + sequenceNum + "BODY:" + body + "HOST:" + hostname; //request message

                send_message(request, serverAddress[0], serverPort, clientSocketWrapper[0]); //calls send_message function (bottom)

                System.out.println("Mail Sent to Server, waiting...");
                
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                System.out.println("Do you want to quit? (quit/no): ");
                boolean quitvalid = false;
                while(true)
                {
                    String quitter = console.nextLine();
                    if (quitter.contains("quit")) //quit the loop and end program
                    {
                        System.out.println("Quiting");
                        quitvalid = true;
                        break ;
                    }
                    else if (quitter.contains("no")) 
                    {
                        quitvalid = false;
                        break ;
                    }
                    else
                    {
                        System.out.print("Invalid input, re-enter: ");
                    }
                }
                if (quitvalid)
                {
                    System.out.println("Sending terminate");
                    send_message("TERMINATE:" + hostMail, serverAddress[0], serverPort, clientSocketWrapper[0]);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    System.exit(0);
                }
                else
                    continue ;
            }
        } catch (IOException e) { //catch IOException (Inputs, files error)
            e.printStackTrace(); //print where the error was
        } finally {
            if (clientSocketWrapper[0] != null && !clientSocketWrapper[0].isClosed()) {
                clientSocketWrapper[0].close(); //close socket after code ends
            }
            console.close();
        }
    }

    private static void handshake(DatagramSocket currentSocket, InetAddress serverAddress, int portNumber) throws IOException
    {
        System.out.println("Connecting...");
        send_message("SYN:" + hostMail , serverAddress, portNumber, currentSocket);
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        currentSocket.receive(receivePacket);
        String ackMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
        if (ackMessage.contains("ACK"))
        {
            System.out.println("Connection established");
            send_message("ACK ACK", serverAddress, portNumber, currentSocket);
        }
        else
        {
            System.out.println("Connection Failed");
            System.exit(0);
        }
    }

    private static void receiveEmail(InetAddress serverAddress, int portNumber, DatagramSocket currentSocket) throws IOException
    {
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        while (true) {
            currentSocket.receive(receivePacket);
            String receivedString = new String(receivePacket.getData(), 0, receivePacket.getLength());
            if (receivedString.contains("SENDER:ACK"))
            {
                String tempStr[] = receivedString.split("ACK:");
                sequenceNum = Integer.parseInt(tempStr[1]);
                System.out.println("ACK:" + sequenceNum + " received");
            }
            if (receivedString.contains("250 OK"))
            {
                String timestamp[] = receivedString.split("250 OK:"); //split the message and take whats after "250 OK:" (the timestamp)
                System.out.println("Email received successfully at " + timestamp[1]);
                System.out.println("Sending ACK");
                send_message("ACK", serverAddress, portNumber, currentSocket);
                String directoryPath = "./" + hostMail + "SenderMails/";
                String filename = subject + "_" + timestamp[1];
                //create sender directory
                String relativeFilePath = directoryPath + filename;
                File directory = new File(directoryPath);
                directory.mkdirs();
                File f = new File(relativeFilePath + ".txt");
                //print file
                PrintWriter fout = new PrintWriter(f);
                fout.println("FROM: " + from);
                fout.println("TO: " + to);
                fout.println("SUBJECT: " + subject);
                fout.println("TIME: " + timestamp[1]);
                fout.println(body);
                fout.close();
                //print attachment file
                if (!attachmentBase64.isEmpty()) {
                    // Decode the Base64 attachment data
                    String splitter[] = attachmentBase64.split(",");
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
                emptyEmailInfo();
            }
            if (receivedString.contains("501 ERROR"))
            {
                System.out.println("501 Error");
                System.out.println("Header files are invalid"); //add option to quit or continue
                System.out.println("Sending ACK");
                send_message("ACK", serverAddress, portNumber, currentSocket);
                emptyEmailInfo();
            }
            if (receivedString.contains("505 ERROR"))
            {
                System.out.println("505 Error");
                System.out.println("Email does not exist");
                System.out.println("Sending ACK");
                send_message("ACK", serverAddress, portNumber, currentSocket);
                emptyEmailInfo();
            }
            if (receivedString.contains("TERMINATE:ACK"))
            {
                System.out.println("ACK received");
                System.out.println("Sending ACK ACK");
                send_message("ACK ACK", serverAddress, portNumber, currentSocket);
                break ;
            }
            if (receivedString.contains("TO:"))
            {   
                String a1[] = receivedString.split("TO:");
                String a2[] = a1[1].split("FROM:");
                String to = a2[0];
                String a3[] = a2[1].split("SUBJECT:");
                String from = a3[0];
                String a4[] = a3[1].split("SEQ:");
                String subject = a4[0];
                String a5[] = a4[1].split("BODY:");
                sequenceNum = Integer.parseInt(a5[0]);
                String a6[] = a5[1].split("ATTACHMENT:");
                String body = a6[0];
                String a7[] = a6[1].split("HOST:");
                String attachmentData = a7[0];

                System.out.println("Mail Received from " + from); //print the hostname of the address
                String directoryPath = "./" + hostMail + "ReceivedMails/";
                String timestamp = java.time.LocalDateTime.now().toString().replace(":", "-");
                String filename = subject + "_" + timestamp;
                String relativeFilePath = directoryPath + filename;
                File directory = new File(directoryPath);
                directory.mkdirs();

                System.out.println("FROM: " + from);
                System.out.println("TO: " + to);
                System.out.println("SUBJECT: " + subject);
                System.out.println("TIME: " + timestamp);
                System.out.println(body);

                System.out.println("Sending ACK:" + (sequenceNum + receivePacket.getLength()));
                send_message("ACK:" + (sequenceNum + receivePacket.getLength()), serverAddress, portNumber, currentSocket);

                File f = new File(relativeFilePath + "txt");

                PrintWriter fout = new PrintWriter(f);
                fout.println("FROM: " + from);
                fout.println("TO: " + to);
                fout.println("SUBJECT: " + subject);
                fout.println("TIME: " + timestamp);
                fout.println(body);
                fout.close();

                //add attachment file
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
            }

        }
    }

    static void send_message(String message, InetAddress serverAddress, int portNumber, DatagramSocket currentSocket)
    {
        try{
            byte[] sendData = message.getBytes(); //convert message to bytes
            int messageBytes = message.getBytes().length; //byte length of request message
            String byteSize = Integer.toString(messageBytes); //parsing int to string
            System.out.println("Message is sending " + byteSize + " Bytes"); //print bytes length
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, portNumber); //create a packet
            currentSocket.send(sendPacket); //send packet(Bytes, Bytes length, address to send, port number)
        } catch(IOException e) { //catch IOException
            e.printStackTrace();
        }
    }

    private static String getFileExtension(File file)
    {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if(dotIndex > 0 && dotIndex < fileName.length() - 1) {
            // Return the substring after the last dot, to the end of the string.
            return fileName.substring(dotIndex + 1);
        } else {
            // No extension found
            return "";
        }
    }

    private static void emptyEmailInfo()
    {
        to = "";
        from = "";
        subject = "";
        body = "";
        attachFile = "";
        attachmentBase64 = "";
    }

}