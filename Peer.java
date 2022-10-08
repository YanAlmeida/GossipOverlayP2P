import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.UUID;


public class Peer {
    private List<String> processedRequests = Collections.synchronizedList(new ArrayList<>());
    private List<String> filesFolder = new ArrayList<String>();
    private List<String> addressesPeers = new ArrayList<String>();
    private InetAddress address;
    private Integer port;
    private DatagramSocket serverSocket;
    private Boolean initialized = false;
    private Scanner scanner;
    private Condition conditionThreadMenu;
    private ThreadRotina threadRotina;

    public Peer(Scanner scannerIn){
        scanner = scannerIn;
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

    public Boolean search(String fileName, InetAddress solicitanteAddress, Integer portaSolicitante, String uuid) throws UnknownHostException {
        String ipPortaFormatado = String.format("%s:%s", getIpAddress(address.getAddress()), port);
        String ipPortaFormatadoSolicitante = String.format("%s:%s", getIpAddress(solicitanteAddress.getAddress()), portaSolicitante);

        if(filesFolder.contains(fileName)){
            System.out.println("Tenho " + fileName + " respondendo para " + ipPortaFormatadoSolicitante);
            Mensagem mensagem = new Mensagem("RESPONSE", ipPortaFormatado, ipPortaFormatadoSolicitante, fileName, uuid);
            enviaMensagem(solicitanteAddress, portaSolicitante, mensagem);
            return false;
        }

        Integer randomIndex = new Random().nextInt(addressesPeers.size());
        String randomPeerIP = addressesPeers.get(randomIndex);
        System.out.println("Não tenho " + fileName + ", encaminhando para " + randomPeerIP);
        Mensagem mensagem = new Mensagem("REQUEST", ipPortaFormatado, ipPortaFormatadoSolicitante, fileName, uuid);
        enviaMensagem(InetAddress.getByName(getIpv4FromIpPort(randomPeerIP)), getPortFromIpPort(randomPeerIP), mensagem);
        return true;
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
            threadRotina = new ThreadRotina(folderPath);
            threadRotina.start();
        }

        public void busca() throws UnknownHostException, InterruptedException {
            System.out.println("Digite o nome do arquivo (com extensão): ");
            String uuid = UUID.randomUUID().toString();
            String filename = scanner.next();
            if(search(filename, address, port, uuid)){
                if(conditionThreadMenu.await(30, TimeUnit.SECONDS)){
                    return;
                }
                System.out.println("Ninguém no sistema possui o arquivo " + filename);
            }
    
        }

        public void run() {
            Boolean loop = true;
            Lock lock = new ReentrantLock();
            conditionThreadMenu = lock.newCondition();
            lock.lock();
            try {
                while(loop) {
                    System.out.println("Selecione uma das opções: \n1: INITIALIZE\n2: SEARCH");
                    String optionSelected = scanner.next();
    
                    switch(optionSelected) {
                        case "1":
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
    
                        case "2":
                            try {
                                busca();
                            }catch (UnknownHostException e) {
                                loop = false;
                                e.printStackTrace();
                            }catch (InterruptedException e){
                                loop = false;
                                e.printStackTrace();
                            }
                        break;
                    }
                }
            } finally {
                lock.unlock();
            }

        }
    }

    private class ThreadServer extends Thread{

        DatagramPacket packet;

        public ThreadServer(DatagramPacket receivedPacket) {
            packet = receivedPacket;
        }

        public void run() {
            try {
                String stringData = new String(packet.getData(), packet.getOffset(), packet.getLength());
                Mensagem mensagemRecebida = new Mensagem(stringData);
                if(processedRequests.contains(mensagemRecebida.requestUUID)){
                    System.out.println("Requisição já recebida.");
                    return;
                }
                processedRequests.add(mensagemRecebida.requestUUID);
                InetAddress solicitanteInicial = InetAddress.getByName(getIpv4FromIpPort(mensagemRecebida.solicitanteInicial));
                Integer portSolicitanteInicial = getPortFromIpPort(mensagemRecebida.solicitanteInicial);
                switch(mensagemRecebida.messageType) {
                    case "REQUEST":
                        search(mensagemRecebida.fileName, solicitanteInicial, portSolicitanteInicial, mensagemRecebida.requestUUID);
                    break;
    
                    case "RESPONSE":
                        conditionThreadMenu.signal();
                    break;
                }
            } catch(UnknownHostException e){
                e.printStackTrace();
            }
        }
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