import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Peer {
    private List<String> filesFolder = new ArrayList<String>();
    private List<String> addressesPeers = new ArrayList<String>();
    private InetAddress address;
    private Integer port;
    private DatagramSocket serverSocket;

    public void inicializa() throws UnknownHostException, SocketException{
        Scanner scanner = new Scanner(System.in);

        System.out.println("Insira o IP: ");
        address = InetAddress.getByName(scanner.nextLine());

        System.out.println("Insira a porta: ");
        port = Integer.parseInt(scanner.nextLine());

        serverSocket = new DatagramSocket(port, address);

        System.out.println("Insira o caminho da pasta: ");
        String folderPath = scanner.nextLine();

        System.out.println("Insira o [IP]:[PORTA] do primeiro peer: ");
        addressesPeers.add(scanner.nextLine());

        System.out.println("Insira o [IP]:[PORTA] do segundo peer: ");
        addressesPeers.add(scanner.nextLine());

        scanner.close();

        ThreadRotina thread_executar = this.new ThreadRotina(folderPath);
        thread_executar.start();
    }

    private class ThreadRotina extends Thread{
        private File folder;

        public ThreadRotina(String folderPath) {
            folder = new File(folderPath);
        }

        public void run() {
            filesFolder = Arrays.asList(folder.listFiles()).stream().map(File::getName).collect(Collectors.toList());
            System.out.println("Arquivos da pasta: " + Arrays.toString(filesFolder.toArray()));
            while(true){
                try {
                    sleep(30000);
                    filesFolder = Arrays.asList(folder.listFiles()).stream().map(File::getName).collect(Collectors.toList());
                    System.out.println(String.format("Sou peer [%s]:[%d] com arquivos %s", getIpAddress(address.getAddress()), port, Arrays.toString(filesFolder.toArray())));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    public DatagramSocket getServerSocket() {
        return serverSocket;
    }

    private static String getIpAddress(byte[] rawBytes) {
        int i = 4;
        StringBuilder ipAddress = new StringBuilder();
        for (byte raw : rawBytes) {
            ipAddress.append(raw & 0xFF);
            if (--i > 0) {
                ipAddress.append(".");
            }
        }
        return ipAddress.toString();
    }

    public static void main(String[] args) throws SocketException, UnknownHostException{
        Peer peer = new Peer();
        peer.inicializa();
    }

}