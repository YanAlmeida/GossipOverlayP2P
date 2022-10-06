import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


public class Peer {
    private List<String> filesFolder = new ArrayList<String>();
    private List<String> addressesPeers = new ArrayList<String>();
    private InetAddress address;
    private Integer port;
    private DatagramSocket serverSocket;
    private Boolean initialized = false;
    ThreadRotina threadRotina;
    private Scanner scanner;

    public Peer(Scanner scannerIn){
        scanner = scannerIn;
    }

    public void inicializa() throws UnknownHostException, SocketException{

        if(threadRotina != null){
            threadRotina.interrupt();
            serverSocket.close();
        }

        System.out.println("Insira o IP: ");
        address = InetAddress.getByName(scanner.next());

        System.out.println("Insira a porta: ");
        port = scanner.nextInt();

        serverSocket = new DatagramSocket(port, address);

        System.out.println("Insira o caminho da pasta: ");
        String folderPath = scanner.next();

        System.out.println("Insira o [IP]:[PORTA] do primeiro peer: ");
        addressesPeers.add(scanner.next());

        System.out.println("Insira o [IP]:[PORTA] do segundo peer: ");
        addressesPeers.add(scanner.next());

        initialized = true;
        threadRotina = this.new ThreadRotina(folderPath);
        threadRotina.start();
    }

    public void busca() throws UnknownHostException{
        System.out.println("Digite o nome do arquivo (com extensão): ");
        String filename = scanner.next();
        search(filename, address, port);
        //incluir aqui a parte que faz cache do que foi processado e da espera do retorno
        // Adicionar mapping com filenames encontrados e o resultado a ser printado, e usar ele para esperar por resposta.
    }

    public void enviaMensagem(InetAddress solicitanteAddress, Integer portaSolicitante, Mensagem mensagem) {
        if (solicitanteAddress.getAddress() == address.getAddress() && port == portaSolicitante) { return; }
        byte[] sendBuffer = new byte[1024];
        sendBuffer = mensagem.toJson().getBytes();
        DatagramPacket packetSend = new DatagramPacket(sendBuffer, sendBuffer.length, solicitanteAddress, portaSolicitante);
        try{
            serverSocket.send(packetSend);
        } catch(IOException e){
            e.printStackTrace();
            return;
        }
    }

    public void search(String fileName, InetAddress solicitanteAddress, Integer portaSolicitante) throws UnknownHostException {
        //Inserir aqui verificação de requisição já processada
        String ipPortaFormatado = String.format("%s:%s", getIpAddress(address.getAddress()), port);
        String ipPortaFormatadoSolicitante = String.format("%s:%s", getIpAddress(solicitanteAddress.getAddress()), portaSolicitante);
        if(filesFolder.contains(fileName)){
            System.out.println("Tenho " + fileName + " respondendo para " + ipPortaFormatadoSolicitante);
            Mensagem mensagem = new Mensagem("RESPONSE", ipPortaFormatado, ipPortaFormatadoSolicitante, fileName);
            enviaMensagem(solicitanteAddress, portaSolicitante, mensagem);
            return;
        }

        Integer randomIndex = new Random().nextInt(addressesPeers.size());
        String randomPeerIP = addressesPeers.get(randomIndex);
        System.out.println("Não tenho " + fileName + ", encaminhando para " + randomPeerIP);
        Mensagem mensagem = new Mensagem("REQUEST", ipPortaFormatado, ipPortaFormatadoSolicitante, fileName);
        enviaMensagem(InetAddress.getByName(getIpv4FromIpPort(randomPeerIP)), getPortFromIpPort(randomPeerIP), mensagem);
        return;
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

    private class ThreadMenu extends Thread{
        public void run() {
            Boolean loop = true;
            while(loop) {
                System.out.println("Selecione uma das opções: \n1: INITIALIZE\n2: SEARCH");
                Integer optionSelected = scanner.nextInt();

                switch(optionSelected) {
                    case 1:
                        try {
                            inicializa();
                        } catch (UnknownHostException e) {
                            loop = false;
                            e.printStackTrace();
                        } catch (SocketException e) {
                            loop = false;
                            e.printStackTrace();
                        }
                    break;

                    case 2:
                        try {
                            busca();
                        }catch (UnknownHostException e) {
                            loop = false;
                            e.printStackTrace();
                        }
                    break;
                }
            }
        }
    }

    private class ThreadServer extends Thread{

        DatagramPacket packet;

        public ThreadServer(DatagramPacket receivedPacket) {
            packet = receivedPacket;
        }

        public void run() {

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

    private static String getIpv4FromIpPort(String ipPortAddress){
        Pattern ipPattern = Pattern.compile("(.+):\\d+");
        Matcher ipMatcher = ipPattern.matcher(ipPortAddress);

        ipMatcher.find();

        return ipMatcher.group(1);
    }

    private static Integer getPortFromIpPort(String ipPortAddress){
        Pattern ipPattern = Pattern.compile(".+:(\\d+)");
        Matcher ipMatcher = ipPattern.matcher(ipPortAddress);

        ipMatcher.find();

        return Integer.parseInt(ipMatcher.group(1));
    }

    public static void main(String[] args) throws SocketException, UnknownHostException, IOException{
        Scanner scanner = new Scanner(System.in);
        Peer peer = new Peer(scanner);
        ThreadMenu threadMenu = peer.new ThreadMenu();
        threadMenu.start();

        while(true){
            if(peer.initialized){
                byte[] recBuffer = new byte[1024];
                DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
                peer.serverSocket.receive(recPkt);
                ThreadServer threadServer = peer.new ThreadServer(recPkt);
                threadServer.start();
            }
        }

    }

}