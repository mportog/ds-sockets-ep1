package p2p;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

// classe para envio de dados
public class Mensagem {
    private Boolean found = false; // var par verificar se o arquivo foi encontrado
    private int type; // guarda o tipo de mensagem
    private final InetSocketAddress originalPeer; // guarda o endereco do peer que solicitou a busca
    private InetSocketAddress peerWithFile; // guarda endereco do peer que possui o arquivo
    private List<InetSocketAddress> checkedPeers; // lista de peers que ja foram consultados
    private final String fileName; // nome do arquivo a ser buscado

    public void updateType(int t) {
        this.type = t;
    } // permite a atualizacao do tipo de mensagem

    public boolean fileFound() {
        return this.found;
    } // retorna se o arquivo foi ou nao encontrado

    // salva o endereco do peer que possui o arquivo
    public void setFileFound(InetAddress addrs, int port) {
        this.found = true;
        this.peerWithFile = new InetSocketAddress(addrs, port);
    }

    // retorna o endereco do peer que possui o arquivo
    public InetSocketAddress getPeerWithFile() {
        return this.peerWithFile;
    }

    // enum com os tipos possiveis de arquivo
    public enum Type {
        UNDEFINED,
        RESPONSE,
        SEARCH
    }

    //contrutor do meu obj mensagem
    public Mensagem(Type t, InetSocketAddress oP, String fN, List<InetSocketAddress> cP) {
        switch (t) {
            case RESPONSE:
                this.type = 1;
                break;
            case SEARCH:
                this.type = 2;
                break;
            default:
                this.type = 0;
                break;
        }
        this.checkedPeers = cP;
        this.fileName = fN;
        this.originalPeer = oP;
    }

    // retorna o nome do arquivo da busca
    public String getFileName() {
        return this.fileName;
    }

    // retorna endereco od peer que solicitou a busca
    public InetSocketAddress getOriginalPeer() {
        return this.originalPeer;
    }

    // retorna a lista de peers que ja foram buscados
    public List<InetSocketAddress> getCheckedPeers() {
        return this.checkedPeers;
    }

    // permite a adicao de um peer na lista
    public void addCheckedPeer(InetSocketAddress checkedAddrs) {
        checkedPeers.add(checkedAddrs);
    }

    // retorna o tipo de peer desta mensagem
    public Type getType() {
        switch (this.type) {
            case 1:
                return Type.RESPONSE;
            case 2:
                return Type.SEARCH;
            default:
                return Type.UNDEFINED;
        }
    }
}
