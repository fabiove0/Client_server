import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client.java
 * -----------------------------------------------------------------------
 * Camada de comunicação do sistema. Implementa a conexão real via socket
 * TCP com o servidor de Chat Corporativo (protocolo texto, linha a linha,
 * definido em br.ufmt.chatcorporativo.protocol.ProtocolConstants).
 *
 * Protocolo (resumo):
 *   C -> S: LOGIN <usuario> <senha> <orgao>
 *   C -> S: MSG <destinatario> <texto...>
 *   C -> S: GMSG <grupo> <texto...>
 *   C -> S: LIST | GLIST | HISTORY
 *   C -> S: GCREATE <grupo> | GJOIN <grupo> | GLEAVE <grupo> | GHISTORY <grupo>
 *   C -> S: FILE <destinatario> <nomeArquivo> <tamanhoBytes>  (+ bytes crus)
 *   C -> S: QUIT
 *
 *   S -> C: OK ...   | ERR <codigo> <mensagem>
 *   S -> C: RECV <remetente> <texto>
 *   S -> C: GRECV <grupo> <remetente> <texto>
 *   S -> C: FRECV <remetente> <nomeArquivo> <tamanhoBytes>  (+ bytes crus)
 * -----------------------------------------------------------------------
 */
public class Client {

    // ---- Recursos de rede ----
    private Socket socket;
    private InputStream entrada;      // stream bruto de leitura (linhas + binário de arquivos)
    private OutputStream saidaBruta;  // stream bruto de escrita (linhas + binário de arquivos)
    private PrintWriter saida;        // escrita de linhas de texto sobre saidaBruta
    private final Object travaEscrita = new Object();
    private Thread threadEscuta;

    private String usuarioAtual;
    private String orgaoAtual;
    private volatile boolean conectado = false;
    private MessageListener listener;

    // Fila de pedidos de histórico pendentes (respondidos em FIFO pelo servidor,
    // já que cada ClientHandler processa um comando por vez, na ordem recebida).
    private final Deque<PendingHistory> historicosPendentes = new ArrayDeque<>();
    private List<MensagemHistorico> historicoEmColeta;
    private PendingHistory historicoAtual;

    // Pasta onde arquivos recebidos são salvos.
    private static final File PASTA_DOWNLOADS = new File("downloads_nexus");

    private static class PendingHistory {
        final String identificador;
        final boolean grupo;

        PendingHistory(String identificador, boolean grupo) {
            this.identificador = identificador;
            this.grupo = grupo;
        }
    }

    // Reconhece linhas de histórico no formato produzido por Message.toString():
    //   "[timestamp] remetente -> destino: conteudo"
    // onde destino pode ser "#grupo" (mensagem de grupo) ou um usuário (mensagem direta).
    private static final Pattern PADRAO_LINHA_HISTORICO =
            Pattern.compile("^\\[(.+?)\\]\\s+(.+?)\\s+->\\s+(.+?):\\s?(.*)$");

    /**
     * Representa uma mensagem de histórico recebida do servidor ao abrir
     * uma conversa (pessoal ou de grupo).
     */
    public static class MensagemHistorico {
        public final String remetente;
        public final String texto;
        public final String hora;
        public final boolean arquivo;

        public MensagemHistorico(String remetente, String texto, String hora, boolean arquivo) {
            this.remetente = remetente;
            this.texto = texto;
            this.hora = hora;
            this.arquivo = arquivo;
        }
    }

    /**
     * Representa um usuário na lista lateral: nome de identificação e o
     * órgão ao qual está vinculado.
     */
    public static class UsuarioInfo {
        public final String nome;
        public final String orgao;

        public UsuarioInfo(String nome, String orgao) {
            this.nome = nome;
            this.orgao = orgao;
        }
    }

    /**
     * Interface de callback usada pelo Client para notificar a camada
     * visual (ChatWindow) sobre eventos assíncronos vindos do servidor.
     */
    public interface MessageListener {
        void onMensagemRecebida(String remetente, String mensagem);
        void onStatusConexaoAlterado(boolean conectado, String detalhes);
        void onListaUsuariosAtualizada(List<UsuarioInfo> usuarios);
        void onArquivoRecebido(String remetente, String nomeArquivo);

        // ---- Grupos ----
        void onListaGruposAtualizada(List<String> grupos);
        void onMensagemGrupoRecebida(String identificadorGrupo, String remetente, String mensagem);
        void onArquivoGrupoRecebido(String identificadorGrupo, String remetente, String nomeArquivo);

        // ---- Histórico (compartilhado entre conversas pessoais e de grupo) ----
        void onHistoricoCarregado(String identificadorConversa, boolean grupo, List<MensagemHistorico> mensagens);
    }

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    /**
     * Conecta ao servidor central de comunicação, realiza o handshake de
     * LOGIN e, em caso de sucesso, inicia a thread de escuta contínua.
     */
    public void conectarServidor(String host, int porta, String nomeUsuario, char[] senha, String orgao) {
        try {
            socket = new Socket(host, porta);
            entrada = socket.getInputStream();
            saidaBruta = socket.getOutputStream();
            saida = new PrintWriter(new OutputStreamWriter(saidaBruta, StandardCharsets.UTF_8), true);

            // Linha de boas-vindas enviada pelo servidor ao aceitar a conexão.
            lerLinha();

            String senhaTexto = senha != null ? new String(senha) : "";
            enviarLinha("LOGIN " + nomeUsuario + " " + senhaTexto + " " + orgao);

            String resposta = lerLinha();
            if (resposta == null) {
                throw new IOException("O servidor encerrou a conexão durante o login.");
            }

            if (resposta.startsWith("OK")) {
                this.usuarioAtual = nomeUsuario;
                this.orgaoAtual = orgao;
                this.conectado = true;

                threadEscuta = new Thread(this::receberMensagem, "nexus-thread-escuta");
                threadEscuta.setDaemon(true);
                threadEscuta.start();

                if (listener != null) {
                    listener.onStatusConexaoAlterado(true, "Conectado a " + host + ":" + porta);
                }

                // Solicita os dados iniciais de contexto (usuários online e grupos).
                enviarLinha("LIST");
                enviarLinha("GLIST");
            } else {
                fecharRecursos();
                if (listener != null) {
                    listener.onStatusConexaoAlterado(false, "Falha no login: " + resposta);
                }
            }
        } catch (IOException e) {
            fecharRecursos();
            if (listener != null) {
                listener.onStatusConexaoAlterado(false, "Erro ao conectar: " + e.getMessage());
            }
        } finally {
            if (senha != null) {
                Arrays.fill(senha, '\0');
            }
        }
    }

    /**
     * Envia uma mensagem de texto para um destinatário (usuário).
     */
    public void enviarMensagem(String destinatario, String mensagem) {
        enviarMensagem(destinatario, mensagem, false);
    }

    /**
     * Variante que permite indicar explicitamente se o destino é um grupo
     * (GMSG) ou um usuário (MSG).
     */
    public void enviarMensagem(String destinatario, String mensagem, boolean grupo) {
        if (!conectado) {
            return;
        }
        String texto = mensagem.replace("\r", " ").replace("\n", " ");
        String comando = (grupo ? "GMSG " : "MSG ") + destinatario + " " + texto;
        enviarLinha(comando);
    }

    /**
     * Loop contínuo (executado em thread dedicada) que lê o stream de
     * entrada do socket e repassa cada evento recebido ao listener.
     */
    public void receberMensagem() {
        try {
            String linha;
            while (conectado && (linha = lerLinha()) != null) {
                processarLinha(linha);
            }
        } catch (IOException e) {
            // Conexão caiu durante a leitura — tratado no finally abaixo.
        } finally {
            boolean estavaConectado = conectado;
            conectado = false;
            fecharRecursos();
            if (estavaConectado && listener != null) {
                listener.onStatusConexaoAlterado(false, "Conexão perdida com o servidor");
            }
        }
    }

    /**
     * Interpreta uma linha vinda do servidor e despacha para o listener
     * adequado.
     */
    private void processarLinha(String linha) throws IOException {
        if (linha.isEmpty()) {
            return;
        }

        // --- Mensagem direta recebida em tempo real ---
        if (linha.startsWith("RECV ")) {
            String resto = linha.substring("RECV ".length());
            int espaco = resto.indexOf(' ');
            if (espaco > 0) {
                String remetente = resto.substring(0, espaco);
                String texto = resto.substring(espaco + 1);
                if (listener != null) {
                    listener.onMensagemRecebida(remetente, texto);
                }
            }
            return;
        }

        // --- Mensagem de grupo recebida em tempo real ---
        if (linha.startsWith("GRECV ")) {
            String resto = linha.substring("GRECV ".length());
            String[] partes = resto.split(" ", 3);
            if (partes.length == 3 && listener != null) {
                listener.onMensagemGrupoRecebida(partes[0], partes[1], partes[2]);
            }
            return;
        }

        // --- Arquivo recebido (cabeçalho seguido dos bytes crus) ---
        if (linha.startsWith("FRECV ")) {
            String resto = linha.substring("FRECV ".length());
            String[] partes = resto.split(" ");
            if (partes.length >= 3) {
                String remetente = partes[0];
                String nomeArquivo = partes[1];
                long tamanho;
                try {
                    tamanho = Long.parseLong(partes[2]);
                } catch (NumberFormatException e) {
                    tamanho = 0;
                }
                File salvo = receberBytesArquivo(nomeArquivo, tamanho);
                if (listener != null) {
                    String nomeParaExibir = salvo != null ? salvo.getName() : nomeArquivo;
                    listener.onArquivoRecebido(remetente, nomeParaExibir);
                }
            }
            return;
        }

        // --- Linha de histórico: "[timestamp] remetente -> destino: texto" ---
        Matcher m = PADRAO_LINHA_HISTORICO.matcher(linha);
        if (m.matches() && historicoEmColeta != null) {
            String timestamp = m.group(1);
            String remetente = m.group(2);
            String destino = m.group(3);
            String texto = m.group(4);

            boolean linhaEhGrupo = destino.startsWith("#");
            boolean pertenceAoPedido;
            if (historicoAtual.grupo) {
                pertenceAoPedido = linhaEhGrupo && destino.substring(1).equals(historicoAtual.identificador);
            } else {
                pertenceAoPedido = !linhaEhGrupo &&
                        ((remetente.equals(historicoAtual.identificador) && destino.equals(usuarioAtual))
                                || (remetente.equals(usuarioAtual) && destino.equals(historicoAtual.identificador)));
            }

            if (pertenceAoPedido) {
                historicoEmColeta.add(new MensagemHistorico(remetente, texto, formatarHora(timestamp), false));
            }
            return;
        }

        // --- Início de um bloco de histórico ---
        if (linha.startsWith("OK Histórico de mensagens diretas") || linha.startsWith("OK Histórico do grupo")) {
            historicoAtual = historicosPendentes.poll();
            historicoEmColeta = new ArrayList<>();
            return;
        }

        // --- Fim de um bloco de histórico ---
        if (linha.startsWith("OK Fim do histórico")) {
            if (historicoAtual != null && listener != null) {
                listener.onHistoricoCarregado(historicoAtual.identificador, historicoAtual.grupo,
                        historicoEmColeta != null ? historicoEmColeta : new ArrayList<>());
            }
            historicoAtual = null;
            historicoEmColeta = null;
            return;
        }

        // --- Lista de usuários online ---
        if (linha.startsWith("OK Usuários online")) {
            int doisPontos = linha.indexOf(':');
            List<UsuarioInfo> usuarios = new ArrayList<>();
            if (doisPontos >= 0 && doisPontos + 1 < linha.length()) {
                String listaTexto = linha.substring(doisPontos + 1).trim();
                if (!listaTexto.isEmpty()) {
                    for (String nome : listaTexto.split(",\\s*")) {
                        String nomeLimpo = nome.trim();
                        if (!nomeLimpo.isEmpty() && !nomeLimpo.equals(usuarioAtual)) {
                            usuarios.add(new UsuarioInfo(nomeLimpo, ""));
                        }
                    }
                }
            }
            if (listener != null) {
                listener.onListaUsuariosAtualizada(usuarios);
            }
            return;
        }

        // --- Lista de grupos ---
        if (linha.startsWith("OK Grupos")) {
            int doisPontos = linha.indexOf(':');
            List<String> grupos = new ArrayList<>();
            if (doisPontos >= 0 && doisPontos + 1 < linha.length()) {
                String listaTexto = linha.substring(doisPontos + 1).trim();
                if (!listaTexto.isEmpty()) {
                    for (String g : listaTexto.split(",\\s*")) {
                        if (!g.trim().isEmpty()) {
                            grupos.add(g.trim());
                        }
                    }
                }
            }
            if (listener != null) {
                listener.onListaGruposAtualizada(grupos);
            }
            return;
        }

        // Demais respostas (OK genérico de MSG/FILE/GCREATE/GJOIN/GLEAVE, ou ERR)
        // são apenas registradas no console — mantendo a interface simples,
        // sem poluir o chat com confirmações redundantes.
        if (linha.startsWith("ERR")) {
            System.err.println("[SERVIDOR] " + linha);
        } else {
            System.out.println("[SERVIDOR] " + linha);
        }
    }

    /** Converte um timestamp ISO ("2026-08-07T10:15:30.123") em "HH:mm" para exibição. */
    private String formatarHora(String timestampIso) {
        try {
            int posT = timestampIso.indexOf('T');
            if (posT >= 0 && timestampIso.length() >= posT + 6) {
                return timestampIso.substring(posT + 1, posT + 6);
            }
        } catch (Exception ignored) {
            // formato inesperado — cai no retorno abaixo
        }
        return timestampIso;
    }

    /**
     * Lê exatamente {@code tamanho} bytes crus do socket (enviados logo após
     * o cabeçalho FRECV) e grava em disco na pasta de downloads.
     */
    private File receberBytesArquivo(String nomeArquivo, long tamanho) throws IOException {
        PASTA_DOWNLOADS.mkdirs();
        File destino = new File(PASTA_DOWNLOADS, nomeArquivo);

        byte[] buffer = new byte[8192];
        long restante = tamanho;
        try (FileOutputStream fos = new FileOutputStream(destino)) {
            while (restante > 0) {
                int aLer = (int) Math.min(buffer.length, restante);
                int lidos = entrada.read(buffer, 0, aLer);
                if (lidos == -1) {
                    break;
                }
                fos.write(buffer, 0, lidos);
                restante -= lidos;
            }
        }
        return destino;
    }

    /**
     * Envia um arquivo (metadados via linha de comando + bytes crus em
     * seguida) para um usuário.
     */
    public void enviarArquivo(File arquivo, String destinatario) {
        if (!conectado || arquivo == null) {
            return;
        }
        try {
            long tamanho = arquivo.length();
            synchronized (travaEscrita) {
                saida.println("FILE " + destinatario + " " + arquivo.getName() + " " + tamanho);
                saida.flush();

                try (FileInputStream fis = new FileInputStream(arquivo)) {
                    byte[] buffer = new byte[8192];
                    int lidos;
                    while ((lidos = fis.read(buffer)) != -1) {
                        saidaBruta.write(buffer, 0, lidos);
                    }
                    saidaBruta.flush();
                }
            }
        } catch (IOException e) {
            if (listener != null) {
                listener.onStatusConexaoAlterado(false, "Erro ao enviar arquivo: " + e.getMessage());
            }
        }
    }

    /**
     * Encerra a conexão com o servidor.
     */
    public void desconectar() {
        if (conectado) {
            enviarLinha("QUIT");
        }
        conectado = false;
        fecharRecursos();
        if (listener != null) {
            listener.onStatusConexaoAlterado(false, "Desconectado");
        }
    }

    // =================================================================
    //  GRUPOS
    // =================================================================

    /**
     * Solicita ao servidor a criação de um novo grupo. A lista de grupos é
     * atualizada em seguida (a resposta chega em onListaGruposAtualizada,
     * processada pela thread de escuta).
     */
    public void criarGrupo(String nomeGrupo) {
        if (!conectado) {
            return;
        }
        enviarLinha("GCREATE " + nomeGrupo);
        enviarLinha("GLIST");
    }

    /**
     * Solicita ao servidor a entrada em um grupo já existente.
     */
    public void entrarGrupo(String identificadorGrupo) {
        if (!conectado) {
            return;
        }
        enviarLinha("GJOIN " + identificadorGrupo);
        enviarLinha("GLIST");
    }

    /**
     * Solicita ao servidor o histórico de mensagens de uma conversa —
     * pessoal (HISTORY, filtrado localmente pelo contato) ou de grupo
     * (GHISTORY, já filtrado pelo servidor).
     */
    public void solicitarHistorico(String identificadorConversa, boolean grupo) {
        if (!conectado) {
            if (listener != null) {
                listener.onHistoricoCarregado(identificadorConversa, grupo, new ArrayList<>());
            }
            return;
        }
        historicosPendentes.add(new PendingHistory(identificadorConversa, grupo));
        if (grupo) {
            enviarLinha("GHISTORY " + identificadorConversa);
        } else {
            enviarLinha("HISTORY");
        }
    }

    public boolean isConectado() {
        return conectado;
    }

    public String getUsuarioAtual() {
        return usuarioAtual;
    }

    public String getOrgaoAtual() {
        return orgaoAtual;
    }

    // =================================================================
    //  UTILITÁRIOS DE PROTOCOLO / SOCKET
    // =================================================================

    private void enviarLinha(String linha) {
        synchronized (travaEscrita) {
            if (saida != null) {
                saida.println(linha);
                saida.flush();
            }
        }
    }

    /**
     * Lê uma linha de texto diretamente do InputStream bruto, sem usar
     * BufferedReader — assim evitamos leitura adiantada (read-ahead) que
     * corromperia os bytes crus enviados após o cabeçalho FRECV (mesma
     * técnica usada pelo servidor em ClientHandler.readLineFromStream).
     */
    private String lerLinha() throws IOException {
        if (entrada == null) {
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = entrada.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                baos.write(b);
            }
        }
        if (b == -1 && baos.size() == 0) {
            return null;
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private void fecharRecursos() {
        try {
            if (saida != null) saida.close();
        } catch (Exception ignored) { }
        try {
            if (entrada != null) entrada.close();
        } catch (Exception ignored) { }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) { }
    }
}
