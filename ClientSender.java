import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.file.Files;

interface EmailEventListener {
    void on250OKReceived(String response);
    void onEmailError(String error);
}

public class ClientSender implements EmailEventListener{ //Client (Email writter)
    public static int sequenceNum = 1;
    private static volatile EmailEventListener listener;
    
    public ClientSender() {
        listener = this;
    }
    public static void main(String[] args) {
        new ClientSender();
        final DatagramSocket[] clientSocketWrapper = new DatagramSocket[1]; //create an empty socket
        Scanner console = new Scanner(System.in); //for user input
        int serverPort = 12121;
        // int sequenceNum = 1;

        try { //error handler to catch io errors
            InetAddress IP = InetAddress.getLocalHost();
            String hostname = IP.getHostName();
            System.out.println("Mail Client Starting at host: "+ hostname); //prints the hostname (DESKTOP-XXXX)
            InetAddress serverAddress = null;
            while (true) {
                try {
                    System.out.print("Type name of Mail servers: ");
                    String mailServer = console.nextLine();
                    serverAddress = InetAddress.getByName(mailServer);
                    break ; 
                } catch (UnknownHostException ex) { 
                    System.out.println("Unknown host name");
                }
            }
            clientSocketWrapper[0] = new DatagramSocket(); //create empty socket object
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            handshake(clientSocketWrapper[0], serverAddress, serverPort);

            Thread receiverThread = new Thread(() -> {
                try{
                    receiveEmail(clientSocketWrapper[0]);
                } catch (IOException e) {
                    System.out.println("Thread error" + e.getMessage());
                }
            });
            receiverThread.start();

            while(true) //infinite loop until break
            {
                System.out.println("Creating New Email.."); //mail inputs
                System.out.print("To: ");
                String to = console.nextLine();
                System.out.print("From: ");
                String from = console.nextLine();
                System.out.print("Subject: ");
                String subject = console.nextLine();
                System.out.print("Body: ");
                String body = console.nextLine();

                String attachmentBase64 = "";
                while(true)
                {
                    System.out.print("Attach a file? (yes/no): ");
                    String attachFile = console.nextLine();
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

                send_message(request, serverAddress, serverPort, clientSocketWrapper[0]); //calls send_message function (bottom)

                System.out.println("Mail Sent to Server, waiting...");
                
                // clientSocketWrapper[0].receive(receivePacket); //save received packet (ACK)
                // String ackString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                // if (ackString.contains("ACK"))
                // {
                //     String tempStr[] = ackString.split("ACK:");
                //     sequenceNum = Integer.parseInt(tempStr[1]);
                //     System.out.println("ACK:" + sequenceNum + " received");
                // }

                //clientSocketWrapper[0].receive(receivePacket);
                String confirmation = new String(receivePacket.getData(), 0, receivePacket.getLength()); //convert packet (bytes) to string
                // if (confirmation.contains("250 OK")) //packet received successfully
                // {
                //     String timestamp[] = confirmation.split("250 OK:"); //split the message and take whats after "250 OK:" (the timestamp)
                //     System.out.println("not Email received successfully at " + timestamp[1]);
                //     System.out.println("Sending ACK");
                //     send_message("ACK", serverAddress, serverPort, clientSocketWrapper[0]);
                //     String directoryPath = "./SenderMails/";
                //     String filename = subject + "_" + timestamp[1];
                //     //create sender directory
                //     String relativeFilePath = directoryPath + filename;
                //     File directory = new File(directoryPath);
                //     directory.mkdirs();

                //     File f = new File(relativeFilePath + ".txt");
                //     //print file
                //     PrintWriter fout = new PrintWriter(f);
                //     fout.println("FROM: " + from);
                //     fout.println("TO: " + to);
                //     fout.println("SUBJECT: " + subject);
                //     fout.println("TIME: " + timestamp[1]);
                //     fout.println(body);
                //     fout.close();
                //     //print attachment file
                //     if (!attachmentBase64.isEmpty()) {
                //         // Decode the Base64 attachment data
                //         String splitter[] = attachmentBase64.split(",");
                //         String attachmentDecode = splitter[0];
                //         String attachmentExtension = splitter[1];
                //         byte[] decodedBytes = Base64.getDecoder().decode(attachmentDecode);

                //         File attachmentFile = new File(relativeFilePath + "_attach." + attachmentExtension);
                //         try (FileOutputStream fos = new FileOutputStream(attachmentFile)) {
                //             fos.write(decodedBytes);
                //             System.out.println("Attachment saved to " + directoryPath);
                //         } catch (IOException e) {
                //             System.out.println("Error saving attachment: " + e.getMessage());
                //         }
                //     }
                // }
                // else if (confirmation.contains("501 Error")) //packet failed
                // {
                //     System.out.println("501 Error");
                //     System.out.println("Header files are invalid"); //add option to quit or continue
                //     System.out.println("Sending ACK");
                //     send_message("ACK", serverAddress, serverPort, clientSocketWrapper[0]);
                // }
                // else if (confirmation.contains("505 Error"))
                // {
                //     System.out.println("505 Error");
                //     System.out.println("Email does not exist");
                //     System.out.println("Sending ACK");
                //     send_message("ACK", serverAddress, serverPort, clientSocketWrapper[0]);
                // }
                // else //unkown error
                // { 
                //     System.out.println("Unknown Error");
                //     System.out.println("Sending ACK");
                //     send_message("ACK", serverAddress, serverPort, clientSocketWrapper[0]);
                //     break ; //quit loop
                // }
                try {
                    // to sleep 10 seconds
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    // recommended because catching InterruptedException clears interrupt flag
                    Thread.currentThread().interrupt();
                    // you probably want to quit if the thread is interrupted
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
                    //send terminate, get ack, send ack ack
                    System.out.println("Sending terminate");
                    send_message("Terminate", serverAddress, serverPort, clientSocketWrapper[0]);
                    clientSocketWrapper[0].receive(receivePacket);
                    String ackTerm = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    if (ackTerm.contains("ACK"))
                    {
                        System.out.println("ACK received");
                        System.out.println("Sending ACK ACK");
                        send_message("ACK ACK", serverAddress, serverPort, clientSocketWrapper[0]);
                        break ;
                    }
                    else
                    {
                        System.out.println("ACK Error");
                    }

                }
                else
                    continue ;
            }
        } catch (IOException e) { //catch IOException (Inputs, files error)
            e.printStackTrace(); //print where the error was
        } finally {
            if (clientSocketWrapper[0] != null && !clientSocketWrapper[0].isClosed()) {
                //clientSocket.close(); //close socket after code ends
            }
            console.close();
        }
    }

    private static void handshake(DatagramSocket currentSocket, InetAddress serverAddress, int portNumber) throws IOException
    {
        System.out.println("Connecting...");
        send_message("SYN-Sender" , serverAddress, portNumber, currentSocket);
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

    private static void receiveEmail(DatagramSocket currentSocket) throws IOException
    {
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        while (true) {
            System.out.println("Thread: Waiting to receive!!");
            currentSocket.receive(receivePacket);
            System.out.println("Thread: received!!");
            String receivedString = new String(receivePacket.getData(), 0, receivePacket.getLength());
            if (receivedString.contains("ACK"))
            {
                String tempStr[] = receivedString.split("ACK:");
                sequenceNum = Integer.parseInt(tempStr[1]);
                System.out.println("2ACK:" + sequenceNum + " received");
            }
            if (receivedString.contains("250 OK"))
            {
                System.out.println("250 ok received");
                listener.on250OKReceived(receivedString);
            }
            if (receivedString.contains("501 ERROR"))
            {
                System.out.println("501 ERROR");
            }
            if (receivedString.contains("505 ERROR"))
            {
                System.out.println("505 ERROR");
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

    public void on250OKReceived

}