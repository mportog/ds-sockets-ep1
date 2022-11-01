package p2p;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Peer {
    static InetSocketAddress myAddress; // guarda o endereco do ip e porta deste peer
    static List<String> localFiles = new ArrayList<>(); // lista guarda os arquivos que contenho na minha pasta
    static List<InetSocketAddress> peersList = new ArrayList<>(); // lista de peer que indiquei ao inicializar
    static Boolean isInitialized = false; // verifica se foi inicializado antes do search
    static Gson gson; // import do Gson para converter meu obj mensagem

    public static void main(String[] args) {
        String stringIn; // var de String pra input do usuario
        DatagramSocket peerSocket = null; // obj do meu sockt -> usao apenas 01 socket para enviar e receber
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        Path path;

        while (true) {
            try {
                // mostra menu
                System.out.println("1 INICIALIZAR | 2 SEARCH");
                stringIn = inFromUser.readLine(); // busca selecao do usuario

                if (stringIn.equals("1")) {
                    System.out.println("Digite o IP:Porta do seu peer:");
                    String enderecoIpPortaPeer = inFromUser.readLine();

                    // converte a string inputada pra ip e porta, no obj de InetSocketAdddress, para criar o DatagramSocket com os dados do meu peer
                    myAddress = new InetSocketAddress(getPeerIP(enderecoIpPortaPeer), getPeerPort(enderecoIpPortaPeer));
                    peerSocket = new DatagramSocket(myAddress);

                    System.out.println("Digite o caminho da pasta com seus arquivos:");
                    stringIn = inFromUser.readLine();
                    path = Paths.get(stringIn);

                    System.out.println("Digite o IP:Porta de outro p2p.Peer (1):");
                    stringIn = inFromUser.readLine();
                    InetAddress ipA = InetAddress.getByName(getPeerIP(stringIn));
                    peersList.add(new InetSocketAddress(ipA, getPeerPort(stringIn)));

                    System.out.println("Digite o IP:Porta de um outro p2p.Peer (2):");
                    stringIn = inFromUser.readLine();
                    InetAddress ipB = InetAddress.getByName(getPeerIP(stringIn));
                    peersList.add(new InetSocketAddress(ipB, getPeerPort(stringIn))); // adiciono os 2 peers de contato na minha lista para randomizar facilmente depois

                    ThreadWatchFolder threadBusca = new ThreadWatchFolder(path, myAddress);
                    threadBusca.start(); // inicio a thread que monitora a pasta

                    ThreadReceiver threadReceiver = new ThreadReceiver(peerSocket, peersList);
                    threadReceiver.start(); // inicio o recebimento de dados do meu peer, caso alguem responda ou queira buscar

                    // ao fim da inicializacao sem entrar no bloco CATCH, indico que meu peer foi inicializado
                    if (!isInit()) {
                        isInitialized = true;
                        gson = new Gson();
                    }

                } else if (stringIn.equals("2")) {
                    if (isInit()) {
                        System.out.println("Digite o nome do arquivo com a extensao a ser buscada:");
                        stringIn = inFromUser.readLine();

                        // ainda nao consultei nenhum peer, na minha thread que vai decidir e adicionar na lista
                        ArrayList<InetSocketAddress> peersAlreadyChecked = new ArrayList<>();

                        // crio o ibj mensagem para envio com os dados da busca
                        Mensagem mensagem = new Mensagem(Mensagem.Type.SEARCH, myAddress, stringIn, peersAlreadyChecked);
                        String msgJson = gson.toJson(mensagem);
                        byte[] sendData = msgJson.getBytes();

                        DatagramPacket sendPacket = new DatagramPacket(sendData, 1, sendData.length, peerSocket.getLocalAddress(), peerSocket.getLocalPort());

                        ThreadSender threadSender = new ThreadSender(sendPacket, peerSocket);
                        threadSender.start();

                    } else {
                        System.out.println("É necessário incializaar o peer");
                    }
                }
            } catch (Exception e) {
                System.out.println("Erro durante execucao: [PeerClient.main] " + e.getLocalizedMessage());
            }
        }
    }

    // retorna se o peer foi inicializado
    private static Boolean isInit() {
        return isInitialized;
    }

    // pega o ip do valor digitado no formto IP:PORTA
    private static String getPeerIP(String peerAddress) {
        return peerAddress.split(":")[0];
    }

    // pega a porta do valor digitado no formto IP:PORTA
    private static int getPeerPort(String peerAddress) {
        return Integer.parseInt(peerAddress.split(":")[1]);
    }

    // thread para monitoara a pasta de arquivos
    static class ThreadWatchFolder extends Thread {
        private final Path path;
        private final InetSocketAddress address; // endereco do peer original, usado no meu unico socket

        public ThreadWatchFolder(Path path, InetSocketAddress addrs) {
            this.path = path;
            this.address = addrs;
        }

        public void run() {
            try {
                while (true) {
                    DirectoryStream<Path> stream = Files.newDirectoryStream(path);
                    localFiles.clear(); // a cada execucao limpo a pasta e a atualizo com os arquivos
                    for (Path entry : stream) {
                        localFiles.add(String.valueOf(entry.getFileName()));
                    }
                    System.out.print("Sou o peer [" + this.address.getAddress().getHostAddress() + ":" + this.address.getPort() + "] com arquivos ");
                    System.out.print(localFiles);
                    System.out.println();
                    sleep(30000); // faco esse thead esperar 30 segundos e ser executada novamente
                }
            } catch (Exception e) {
                System.out.println("Erro durante execucao: [ThreadBuscaPasta.run] " + e.getLocalizedMessage());
            }
        }
    }

    // thread para receber dados no meu socket
    static class ThreadReceiver extends Thread {
        private final DatagramSocket socket; // recebo a mesma instancia do meu socket para receber
        private final List<InetSocketAddress> peersList; // lista de peers a ser randomizada para continuar o envio

        // thread para receber requisicoes ou respostas
        public ThreadReceiver(DatagramSocket peerSocket, List<InetSocketAddress> pL) {
            this.socket = peerSocket;
            this.peersList = pL;
        }

        public void run() {

            while (currentThread().isAlive()) {

                byte[] recBuffer = new byte[1024];

                DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length); // crio um pacote para ser recebido
                try {
                    socket.receive(recPkt); // meu socket recebe

                    String msgJsonObj = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength()); // converto para string os dados recebidos em bytearray
                    Mensagem msg = gson.fromJson(msgJsonObj, Mensagem.class); // converto a string recebida para meu obj Mensagem
                    String receivedMessage = msg.getFileName();

                    processMessage(msg, receivedMessage, recPkt); // verifico que tipo de recebimento foi

                } catch (Exception e) {
                    System.out.println("Erro durante execucao: [ThreadReceiver.run] " + e.getLocalizedMessage());
                }
            }
        }

        // encaminho para um dos meus 2 peers de contato ou respondo o peer solicitante
        private void sendReplyOrRequest(String fileName, Mensagem receivedMessage, DatagramPacket recPkt) {

            // se possuo o arquivo, respondo o peer original com meu endereco na mensagem
            if (localFiles.contains(fileName)) {
                // busco endereco do peer original
                String originalPeerIp = receivedMessage.getOriginalPeer().getAddress().getHostAddress();
                int originalPeerPort = receivedMessage.getOriginalPeer().getPort();
                System.out.println("Tenho [" + fileName + "]. Respondendo para [" + originalPeerIp + ":" + originalPeerPort + "]");
                receivedMessage.setFileFound(myAddress.getAddress(), myAddress.getPort()); // gravo meu endereco na mensagem
            } else {
                // nao possuo o arquivo, randomizo meus contatos para ser o proximo a ser consultado
                int random = new Random().nextInt(2);
                InetSocketAddress choosedPeer = peersList.get(random);

                // o peer sorteado ja foi consultado, nao devo refazer
                if (receivedMessage.getCheckedPeers().contains(choosedPeer)) {
                    // peer ja consultado, paro minha consulta aqui, envio uma RESPONSE para o PEER ORIGINAL
                    receivedMessage.updateType(Mensagem.Type.RESPONSE.ordinal()); // mudo o tipo de mensagem
                    recPkt.setAddress(receivedMessage.getOriginalPeer().getAddress()); // marco que é para enviar para o peer que solicitou a busca
                    recPkt.setPort(receivedMessage.getOriginalPeer().getPort());

                } else {
                    // envia o search pro peer escolhido aleatoriamente
                    recPkt.setAddress(choosedPeer.getAddress());
                    recPkt.setPort(choosedPeer.getPort());

                    // adiciono o peer na lista de peers que foram consultados que sera enviada na mesagem
                    receivedMessage.addCheckedPeer(choosedPeer);

                    System.out.println("Não tenho [" + fileName + "], encaminhado para " + recPkt.getAddress() + ":" + recPkt.getPort());
                }
                // converto a mensagem para json
                String msgJson = gson.toJson(receivedMessage);

                byte[] sendData = msgJson.getBytes();
                recPkt.setData(sendData, 1, sendData.length);

                //envia nova mensagem
                Peer.ThreadSender threadReSender = new Peer.ThreadSender(recPkt, socket);
                threadReSender.start();

            }
        }

        // verifica qual o tipo da mengeam é, pra saber se respondo/encaminho ou se é um retorno do meu search
        private void processMessage(Mensagem msg, String fN, DatagramPacket rP) {
            switch (msg.getType()) {
                case SEARCH:
                    sendReplyOrRequest(fN, msg, rP);
                    break;
                case RESPONSE:
                    // print mensagem de resposta caso tenha ou nao
                    if (msg.fileFound()) {
                        System.out.println("Peer com arquivo procurado: [" + msg.getFileName() + ":" + msg.getPeerWithFile().getAddress().getHostName() + "] [" + msg.getPeerWithFile().getPort() + "]");
                    } else {
                        System.out.println("Ninguém no sistema possui o arquivo [" + msg.getFileName() + "]");
                    }
                    break;
                default:
                    System.out.println("Tipo de mensagem nao definida");
                    break;
            }
        }
    }

    // thread para enviar dados no meu socket
    static class ThreadSender extends Thread {
        private final DatagramSocket socket; //recebo a instancia do meu socket para enviar
        private final DatagramPacket sendPacket;

        public ThreadSender(DatagramPacket sendPacket, DatagramSocket peerSocket) {
            this.socket = peerSocket;
            this.sendPacket = sendPacket;
        }

        public void run() {
            try {
                socket.send(sendPacket); //envio o pacote via meu sockt
            } catch (IOException e) {
                System.out.println("Erro durante execucao: [ThreadSender.run] " + e.getLocalizedMessage());
                e.printStackTrace();
            }
        }
    }
}
