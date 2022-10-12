package br.com.ufabc.redeBuscaArquivos;

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
import java.util.concurrent.ConcurrentHashMap;
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
    private List<String> processedResponses = Collections.synchronizedList(new ArrayList<>());
    private ConcurrentHashMap<String, List<String>> filesFolder = new ConcurrentHashMap<String, List<String>>();
    private List<String> addressesPeers = Collections.synchronizedList(new ArrayList<>());
    private InetAddress address;
    private Integer port;
    private String folderPath;
    private DatagramSocket serverSocket;
    private volatile Boolean initialized = false;
    private Scanner scanner;
    private Lock lockThreadMenu;
    private Condition conditionThreadMenu;
    private ThreadRotina threadRotina;

    public Peer(Scanner scannerIn){
        /* Construtor recebendo scanner para uso em diversos métodos/threads. */
        scanner = scannerIn;
    }

    public void enviaMensagem(InetAddress destinoAddress, Integer portaDestino, Mensagem mensagem) {
        /* Método para enviar mensagem através do socket UDP para um endereço e porta de destino */
        byte[] sendBuffer = new byte[1024];
        sendBuffer = mensagem.toJson().getBytes();
        DatagramPacket packetSend = new DatagramPacket(sendBuffer, sendBuffer.length, destinoAddress, portaDestino);
        try{
            serverSocket.send(packetSend);
        } catch(IOException e){
            e.printStackTrace();
            return;
        }
    }

    public Boolean search(String fileName, InetAddress solicitanteAddress, Integer portaSolicitante, String uuid) throws UnknownHostException {
        /* Método para efetuar busca por arquivo e responde para solicitante caso possua.
         * Caso não possua, pergunta a outro peer aleatório 
        */
        String ipPortaFormatado = String.format("%s:%s", getIpAddress(address.getAddress()), port);
        String ipPortaFormatadoSolicitante = String.format("%s:%s", getIpAddress(solicitanteAddress.getAddress()), portaSolicitante);

        if(filesFolder.get(folderPath).contains(fileName)){
            if (ipPortaFormatado.equals(ipPortaFormatadoSolicitante)) { 
                System.out.println(String.format("Eu (%s) possuo o arquivo %s", ipPortaFormatadoSolicitante, fileName));
             }else{
                System.out.println("Tenho " + fileName + " respondendo para " + ipPortaFormatadoSolicitante);
                Mensagem mensagem = new Mensagem("RESPONSE", ipPortaFormatado, ipPortaFormatadoSolicitante, fileName, uuid);
                enviaMensagem(solicitanteAddress, portaSolicitante, mensagem);
             }
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
        /* Classe aninhada que representa a thread da rotina de verificação da pasta selecionada e atualização da estrutura */
        private File folder = new File(folderPath);

        public void run() {
            /* Execução da ThreadRotina, que inicialmente exibe os arquivos da pasta no formato de lista e, então,
             * executa um laço para atualizar o ConcurrentHashMap e exibir os arquivos armazenados nele a cada 30 segundos
            */
            filesFolder.put(folderPath, Arrays.asList(folder.listFiles()).stream().map(File::getName).collect(Collectors.toList()));
            System.out.println("Arquivos da pasta: " + filesFolder.get(folderPath));
            while(true){
                try {
                    sleep(30000);
                    filesFolder.put(folderPath, Arrays.asList(folder.listFiles()).stream().map(File::getName).collect(Collectors.toList()));
                    System.out.println(String.format("Sou peer %s:%d com arquivos %s", getIpAddress(address.getAddress()), port, filesFolder.get(folderPath)));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private class ThreadMenu extends Thread{
        /* Classe aninhada que representa a thread que executa o menu interativo e possui os métodos
         * correspondentes a suas opções
         */

        public void inicializa() throws UnknownHostException, SocketException{
            /* Método correspondente à opção "INITIALIZE" do menu. Responsável por colher dados como:
             * O endereço/porta do peer, a pasta a monitorar e os endereços/portas dos outros dois peers,
             * além de inicializar a thread rotina.
             */
            if(threadRotina != null){
                initialized = false;
                threadRotina.interrupt();
                serverSocket.close();
            }

            System.out.println("Insira o IP: ");
            address = InetAddress.getByName(scanner.next());

            System.out.println("Insira a porta: ");
            port = scanner.nextInt();

            serverSocket = new DatagramSocket(port, address);

            System.out.println("Insira o caminho da pasta: ");
            folderPath = scanner.next();

            System.out.println("Insira o [IP]:[PORTA] do primeiro peer: ");
            addressesPeers.add(scanner.next());

            System.out.println("Insira o [IP]:[PORTA] do segundo peer: ");
            addressesPeers.add(scanner.next());

            initialized = true;
            threadRotina = new ThreadRotina();
            threadRotina.start();
        }

        public Boolean busca(String filename) throws UnknownHostException, InterruptedException {
            /* Método correspondente à opção "SEARCH" do menu, responsável por gerar um UUID para a operação
             * e por estabelecer o timeout da mesma através do uso de locks/conditions, possibilitando os retries
             * automáticos
             */
            String uuid = UUID.randomUUID().toString();
            processedRequests.add(uuid); // Para evitar que a requisição seja reprocessada pelo peer
            if(search(filename, address, port, uuid)){
                if(conditionThreadMenu.await(1250, TimeUnit.MILLISECONDS)){
                    return true;
                }
                return false;
            }
            return true;
        }

        public void run() {
            /* Execução da ThreadMenu, executa um loop para exibir as opções e colher a escolha do usuário,
             * executando o método correspondente
             */
            Boolean loop = true;
            lockThreadMenu = new ReentrantLock();
            conditionThreadMenu = lockThreadMenu.newCondition();
            lockThreadMenu.lock();
            try {
                while(loop) {
                    System.out.println("Selecione uma das opções: \n1: INITIALIZE\n2: SEARCH");
                    String optionSelected = scanner.next();
    
                    switch(optionSelected) {
                        case "1":
                            try {
                                inicializa();
                            } catch (UnknownHostException e) {
                                e.printStackTrace();
                            } catch (SocketException e) {
                                e.printStackTrace();
                            }
                        break;
    
                        case "2":
                            try {
                                if(initialized){
                                    System.out.println("Digite o nome do arquivo (com extensão): ");
                                    String fileName = scanner.next();
                                    Integer attempts = 1;
                                    while(attempts <= 4){
                                        if(busca(fileName)){
                                            break;
                                        }
                                        System.out.println(attempts + " tentativa falhou. Tentando novamente..."); 
                                        attempts++;
                                    }
                                    if(attempts > 4){
                                        System.out.println("Ninguém no sistema possui o arquivo " + fileName);
                                    }
                                }else{
                                    System.out.println("Inicialize o peer antes de iniciar uma busca.");
                                }
                            }catch (UnknownHostException e) {
                                e.printStackTrace();
                            }catch (InterruptedException e){
                                e.printStackTrace();
                            }
                        break;
                    }
                }
            } finally {
                lockThreadMenu.unlock();
            }

        }
    }

    private class ThreadServer extends Thread{
        /* Classe aninhada que representa a thread responsável pelo recebimento e processamento (ou descarte)
         * das requisições
        */
        DatagramPacket packet;

        public ThreadServer(DatagramPacket receivedPacket) {
            /* Construtor recebe o datagrama enviado */
            packet = receivedPacket;
        }

        public void run() {
            /* Execução da ThreadServer, realiza a extração da mensagem transmitida, verifica se a requisição
             * já foi processada através do UUID e, dependendo do tipo da mensagem ("REQUEST" ou "RESPONSE"),
             * direciona para busca ou envia um signal para a condition na ThreadRotina, evitando o timeout
             */
            try {
                String stringData = new String(packet.getData(), packet.getOffset(), packet.getLength());
                Mensagem mensagemRecebida = new Mensagem(stringData);
                InetAddress solicitanteInicial = InetAddress.getByName(getIpv4FromIpPort(mensagemRecebida.solicitanteInicial));
                Integer portSolicitanteInicial = getPortFromIpPort(mensagemRecebida.solicitanteInicial);
                switch(mensagemRecebida.messageType) {
                    case "REQUEST":
                        if(processedRequests.contains(mensagemRecebida.requestUUID)){
                            System.out.println("Requisição já processada para " + mensagemRecebida.fileName);
                            return;
                        }
                        processedRequests.add(mensagemRecebida.requestUUID);
                        search(mensagemRecebida.fileName, solicitanteInicial, portSolicitanteInicial, mensagemRecebida.requestUUID);
                    break;
    
                    case "RESPONSE":
                        if(processedResponses.contains(mensagemRecebida.requestUUID)){
                            System.out.println("Resposta já processada para " + mensagemRecebida.fileName);
                            return;
                        }
                        processedResponses.add(mensagemRecebida.requestUUID);
                        lockThreadMenu.lock();
                        try {
                            conditionThreadMenu.signal();
                            System.out.println("Peer com arquivo procurado: " + mensagemRecebida.sender + " " + mensagemRecebida.fileName);
                        } catch(Exception e){
                            e.printStackTrace();
                        }finally {
                            lockThreadMenu.unlock();
                        }
                        
                    break;
                }
            } catch(UnknownHostException e){
                e.printStackTrace();
            }
        }
    }

    private static String getIpAddress(byte[] rawBytes) {
        /* Método para conversão de array de bytes em endereço IPv4 */
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
        /* Método para extração do endereço de IP de string com formato IP:PORTA */
        Pattern ipPattern = Pattern.compile("(.+):\\d+");
        Matcher ipMatcher = ipPattern.matcher(ipPortAddress);

        ipMatcher.find();

        return ipMatcher.group(1);
    }

    private static Integer getPortFromIpPort(String ipPortAddress){
        /* Método para extração da porta de string com formato IP:PORTA */
        Pattern ipPattern = Pattern.compile(".+:(\\d+)");
        Matcher ipMatcher = ipPattern.matcher(ipPortAddress);

        ipMatcher.find();

        return Integer.parseInt(ipMatcher.group(1));
    }

    public static void main(String[] args) throws UnknownHostException, IOException{
        /* Método main, responsável por instanciar o peer, startar a thread menu e ouvir novas requisições/startar
         * a thread server após inicialização
        */
        Scanner scanner = new Scanner(System.in);
        Peer peer = new Peer(scanner);
        ThreadMenu threadMenu = peer.new ThreadMenu();
        threadMenu.start();

        while(true){
            if(peer.initialized){
                byte[] recBuffer = new byte[1024];
                DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
                try{
                    peer.serverSocket.receive(recPkt);
                    ThreadServer threadServer = peer.new ThreadServer(recPkt);
                    threadServer.start();
                }catch(SocketException e){
                    if(peer.initialized){
                        System.out.println("Algo deu errado. Inicialize o peer novamente.");
                        peer.initialized = false;
                    }
                    continue;
                }

            }
        }

    }

}