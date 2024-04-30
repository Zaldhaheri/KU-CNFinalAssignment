import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.file.Files;

public class ClientSender{
    public static int sequenceNum = 1;
    public static String hostMail, to, from, subject, body, attachFile, attachmentBase64 = "";

    public static void main(String[] args) {
        //global variables
        final DatagramSocket[] clientSocketWrapper = new DatagramSocket[1];
        final InetAddress[] serverAddress = new InetAddress[1];
        Scanner console = new Scanner(System.in);
        int serverPort = 12121;
        
        try {
            InetAddress IP = InetAddress.getLocalHost();
            String hostname = IP.getHostName();
            System.out.println("Mail Client Starting at host: "+ hostname); //prints the hostname (DESKTOP-XXXX)
            hostMail = "";
            //enter email (valid input only)
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
            //enter mail server (valid input only)
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
            //perform handshake
            clientSocketWrapper[0] = new DatagramSocket();
            handshake(clientSocketWrapper[0], serverAddress[0], serverPort);

            //create a thread to receive packets
            Thread receiverThread = new Thread(() -> {
                try{
                    receiveEmail(serverAddress[0], serverPort, clientSocketWrapper[0]);
                } catch (IOException e) {
                    System.out.println("Thread error" + e.getMessage());
                }
            });
            receiverThread.start();

            //create email process
            while(true)
            {
                System.out.println("Creating New Email.."); //mail inputs
                System.out.print("To (separate multiple emails with ';'): ");
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
                String request = "TO:" + to + "FROM:" + from + "SUBJECT:" + subject + "SEQ:" + sequenceNum + "BODY:" + body + "ATTACHMENT:" + attachmentBase64 + "HOST:" + hostname + "(END)";
                send_message(request, serverAddress[0], serverPort, clientSocketWrapper[0]); //calls send_message function (bottom)
                System.out.println("Mail Sent to Server");
                //sleep program before asking to quit (so all processes by the thread are done)
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                //quitting sequence
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
                        Thread.sleep(1000);
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

    //performs the 3way handshake
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

    //thread that handles received packets
    private static void receiveEmail(InetAddress serverAddress, int portNumber, DatagramSocket currentSocket) throws IOException
    {
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        while (true) {
            currentSocket.receive(receivePacket);
            String receivedString = new String(receivePacket.getData(), 0, receivePacket.getLength());
            //ack handles sequence numbers
            if (receivedString.contains("SENDERACK:"))
            {
                String tempStr[] = receivedString.split("ACK:");
                sequenceNum = Integer.parseInt(tempStr[1]);
                System.out.println("ACK:" + sequenceNum + " received");
            }
            //handles 250 OK and 200 OK(pending emails)
            if (receivedString.contains("250 OK") || receivedString.contains("200 OK"))
            {
                String timestamp[];
                if (receivedString.contains("250 OK"))
                {
                    timestamp = receivedString.split("250 OK:"); //split the message and take whats after "250 OK:" (the timestamp)
                    System.out.println("250 OK: Email received successfully at " + timestamp[1]);
                }
                else
                {
                    timestamp = receivedString.split("200 OK:"); //split the message and take whats after "250 OK:" (the timestamp)
                    System.out.println("200 OK: Email received successfully at " + timestamp[1]);
                }
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
            //501 ERROR packets
            if (receivedString.contains("501 ERROR"))
            {
                System.out.println("501 Error");
                System.out.println("Header files are invalid"); //add option to quit or continue
                System.out.println("Sending ACK");
                send_message("ACK", serverAddress, portNumber, currentSocket);
                emptyEmailInfo();
            }
            //505 ERROR packets
            if (receivedString.contains("505 ERROR"))
            {
                System.out.println("505 Error");
                System.out.println("Email does not exist");
                System.out.println("Sending ACK");
                send_message("ACK", serverAddress, portNumber, currentSocket);
                emptyEmailInfo();
            }
            //handles termination and exits the thread
            if (receivedString.contains("TERMINATE:ACK"))
            {
                System.out.println("ACK received");
                System.out.println("Sending ACK ACK");
                send_message("ACK ACK", serverAddress, portNumber, currentSocket);
                break ;
            }
            //handles received emails
            if (receivedString.contains("TO:"))
            {
                String tempMessage;
                //handles multiple packets
                while(!receivedString.contains("(END)"))
                {
                    currentSocket.receive(receivePacket);
                    tempMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    receivedString = receivedString + tempMessage;
                }
                String temp[] = receivedString.split("(END)");
                receivedString = temp[0];
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
                String a8[] = a7[1].split("TIME:");
                String timestamp = a8[1];

                System.out.println("Mail Received from " + from); //print the hostname of the address
                String directoryPath = "./" + hostMail + "ReceivedMails/";
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

    //sends message with multiple packet handling
    static void send_message(String message, InetAddress serverAddress, int portNumber, DatagramSocket currentSocket)
    {
        try{
            final int MAX_SEGMENT_SIZE = 1024;
            byte[] messageBytes = message.getBytes();
            int messageLength = messageBytes.length;
            int start = 0;
        
            while (start < messageLength) {
                int end = Math.min(start + MAX_SEGMENT_SIZE, messageLength);
                byte[] segment = Arrays.copyOfRange(messageBytes, start, end);
                System.out.println("Message is sending " + segment.length + " Bytes"); //print bytes length
                DatagramPacket sendPacket = new DatagramPacket(segment, segment.length, serverAddress, portNumber);
                currentSocket.send(sendPacket);
                start = end;
            }
        } catch(IOException e) { //catch IOException
            e.printStackTrace();
        }
    }

    //gets the extension (.pdf,.txt,etc.) of a file
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

    //resets the global email info
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