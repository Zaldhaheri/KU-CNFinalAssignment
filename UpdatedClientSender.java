import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientSender {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private Scanner console;

    public ClientSender() throws SocketException, UnknownHostException {
        this.socket = new DatagramSocket();
        this.console = new Scanner(System.in);
        System.out.print("Type name of Mail servers: ");
        String mailServer = console.nextLine();
        this.serverAddress = InetAddress.getByName(mailServer);
    }

    public void start() {
        Thread sendThread = new Thread(this::send);
        Thread receiveThread = new Thread(this::receive);
        sendThread.start();
        receiveThread.start();

        try {
            sendThread.join();
            receiveThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Threads were interrupted.");
        }
    }

    private void send() {
        try {
            while (true) {
                System.out.print("Enter your message: ");
                String msg = console.nextLine();
                byte[] buf = msg.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddress, 1234);
                socket.send(packet);
            }
        } catch (IOException e) {
            System.out.println("IOException in send method: " + e.getMessage());
        }
    }

    private void receive() {
        try {
            byte[] buf = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received: " + received);
            }
        } catch (IOException e) {
            System.out.println("IOException in receive method: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            ClientSender client = new ClientSender();
            client.start();
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
