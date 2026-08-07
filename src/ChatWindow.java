import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * ChatWindow.java
 * -----------------------------------------------------------------------
 * Janela principal do cliente "NEXUS.gov — Comunicação Corporativa".
 * Contém APENAS a interface gráfica (Swing). Nenhuma chamada de rede é
 * feita diretamente aqui: os botões apenas invocam os métodos do Client.
 *
 * Suporta conversas PESSOAIS e de GRUPO. Em ambos os casos, ao abrir a
 * conversa o histórico é solicitado ao Client (ainda simulado — ver
 * Client.solicitarHistorico) e exibido assim que chega via
 * onHistoricoCarregado().
 * -----------------------------------------------------------------------
 */
public class ChatWindow extends JFrame implements Client.MessageListener {

    // ---------------------------------------------------------------
    // Paleta de cores / identidade visual (tema escuro, minimalista).
    // Espelhada em styles/style.css para referência de design.
    // ---------------------------------------------------------------
    public static class Theme {
        public static final Color BG_PRIMARY     = new Color(0x12, 0x14, 0x18);
        public static final Color BG_SURFACE     = new Color(0x1A, 0x1D, 0x23);
        public static final Color BG_ELEVATED    = new Color(0x22, 0x26, 0x2E);
        public static final Color BG_INPUT       = new Color(0x1E, 0x22, 0x29);
        public static final Color BORDER_SUBTLE  = new Color(0x2A, 0x2E, 0x37);

        public static final Color ACCENT         = new Color(0x2E, 0x86, 0xFF);
        public static final Color ACCENT_HOVER    = new Color(0x1E, 0x6F, 0xE0);
        public static final Color ACCENT_SOFT     = new Color(0x22, 0x3A, 0x5C);

        public static final Color SUCCESS         = new Color(0x3D, 0xDC, 0x97);
        public static final Color DANGER          = new Color(0xE5, 0x5A, 0x5A);
        public static final Color WARNING         = new Color(0xE8, 0xB4, 0x4C);

        public static final Color TEXT_PRIMARY    = new Color(0xF2, 0xF3, 0xF5);
        public static final Color TEXT_SECONDARY  = new Color(0x9A, 0xA1, 0xAC);
        public static final Color TEXT_MUTED      = new Color(0x6B, 0x72, 0x7D);

        public static final Font FONT_BRAND    = new Font("Segoe UI", Font.BOLD, 18);
        public static final Font FONT_TITLE    = new Font("Segoe UI", Font.BOLD, 14);
        public static final Font FONT_BODY     = new Font("Segoe UI", Font.PLAIN, 13);
        public static final Font FONT_SMALL    = new Font("Segoe UI", Font.PLAIN, 11);
    }

    // ---- Registro interno de uma mensagem (para redesenhar conversas) ----
    private static class MensagemRegistrada {
        String remetente;
        String texto;
        boolean propria;
        boolean arquivo;
        String hora;

        /** Construtor para mensagens novas, enviadas/recebidas em tempo real. */
        MensagemRegistrada(String remetente, String texto, boolean propria, boolean arquivo) {
            this(remetente, texto, propria, arquivo, new SimpleDateFormat("HH:mm").format(new Date()));
        }

        /** Construtor para mensagens vindas do histórico, com horário já definido. */
        MensagemRegistrada(String remetente, String texto, boolean propria, boolean arquivo, String hora) {
            this.remetente = remetente;
            this.texto = texto;
            this.propria = propria;
            this.arquivo = arquivo;
            this.hora = hora;
        }
    }

    // ---- Componentes principais ----
    private final Client client;
    private final FileManager fileManager = new FileManager();
    private String commandLineArgs[];
    private String domainId;
    private int port;

    // Barra superior — status
    private JLabel statusDot;
    private JLabel statusLabel;

    // Barra superior — cartão de login (antes de conectar)
    private JTextField campoUsuarioLogin;
    private JPasswordField campoSenhaLogin;
    private JTextField campoOrgaoLogin;
    private RoundedButton botaoConectar;

    // Barra superior — cartão conectado (depois de conectar)
    private JLabel labelUsuarioConectado;
    private JLabel labelOrgaoConectado;
    private RoundedButton botaoDesconectar;

    private CardLayout cardLayoutConexao;
    private JPanel painelConexaoCards;
    private static final String CARD_LOGIN = "login";
    private static final String CARD_CONECTADO = "conectado";

    // Painel lateral — seletor de aba (Usuários / Grupos)
    private CardLayout cardLayoutLateral;
    private JPanel painelLateralCards;
    private AbaLateralBotao abaUsuariosBotao;
    private AbaLateralBotao abaGruposBotao;
    private static final String CARD_ABA_USUARIOS = "aba_usuarios";
    private static final String CARD_ABA_GRUPOS = "aba_grupos";

    // Painel lateral — aba Usuários
    private JTextField campoBusca;
    private DefaultListModel<Client.UsuarioInfo> modeloListaUsuarios;
    private JList<Client.UsuarioInfo> listaUsuarios;
    private final List<Client.UsuarioInfo> todosUsuarios = new ArrayList<>();
    private final Set<String> usuariosComNaoLidas = new HashSet<>();

    // Painel lateral — aba Grupos
    private JTextField campoBuscaGrupos;
    private DefaultListModel<String> modeloListaGrupos;
    private JList<String> listaGrupos;
    private final List<String> todosGrupos = new ArrayList<>();
    private final Set<String> gruposComNaoLidas = new HashSet<>();

    // Painel de chat
    private JLabel tituloConversa;
    private JTextField campoMensagem;
    private RoundedButton botaoEnviar;
    private BotaoAnexo botaoAnexar;
    private JTextPane areaMensagens;
    private StyledDocument documentoMensagens;

    // Estado da conversa ativa (pode ser um usuário OU um grupo)
    private String conversaAtual = null;
    private boolean conversaAtualEhGrupo = false;

    // Histórico local por conversa. A chave combina tipo + identificador
    // (ver chaveConversa) para não confundir um usuário e um grupo de
    // mesmo nome.
    private final Map<String, List<MensagemRegistrada>> conversas = new LinkedHashMap<>();

    public ChatWindow(Client client, String args[]) {
        super("NEXUS.gov — Comunicação Corporativa");
        this.client = client;
        this.commandLineArgs = args;

        configurarJanela();
        initComponents();
    }

    private void configurarJanela() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setSize(1220, 760);
        getContentPane().setBackground(Theme.BG_PRIMARY);
        // Ícone da aplicação: substituir futuramente por um arquivo real
        // em src/assets/ (ex.: assets/app-icon.png).
        // setIconImage(new ImageIcon("src/assets/app-icon.png").getImage());
    }

    private void initComponents() {
        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(Theme.BG_PRIMARY);
        raiz.setBorder(new EmptyBorder(16, 16, 16, 16));

        raiz.add(construirBarraSuperior(), BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                construirPainelLateral(),
                construirPainelChat()
        );
        splitPane.setResizeWeight(0);
        splitPane.setDividerSize(12);
        splitPane.setBorder(null);
        splitPane.setBackground(Theme.BG_PRIMARY);
        splitPane.setOpaque(false);

        raiz.add(splitPane, BorderLayout.CENTER);

        setContentPane(raiz);
    }

    // =================================================================
    //  BARRA SUPERIOR — nome do sistema + login / usuário conectado
    //  (sem alterações de lógica em relação à versão anterior)
    // =================================================================
    private JComponent construirBarraSuperior() {
        RoundedPanel topo = new RoundedPanel(16, Theme.BG_SURFACE);
        topo.setLayout(new BorderLayout());
        topo.setBorder(new EmptyBorder(14, 20, 14, 20));
        topo.setPreferredSize(new Dimension(10, 80));

        JPanel marca = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        marca.setOpaque(false);

        JLabel logo = new JLabel("◆");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        logo.setForeground(Theme.ACCENT);

        JPanel textoMarca = new JPanel();
        textoMarca.setOpaque(false);
        textoMarca.setLayout(new BoxLayout(textoMarca, BoxLayout.Y_AXIS));
        JLabel nomeSistema = new JLabel("NEXUS.gov");
        nomeSistema.setFont(Theme.FONT_BRAND);
        nomeSistema.setForeground(Theme.TEXT_PRIMARY);
        JLabel subtitulo = new JLabel("Plataforma Nacional de Comunicação Corporativa");
        subtitulo.setFont(Theme.FONT_SMALL);
        subtitulo.setForeground(Theme.TEXT_SECONDARY);
        textoMarca.add(nomeSistema);
        textoMarca.add(subtitulo);

        marca.add(logo);
        marca.add(textoMarca);

        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        direita.setOpaque(false);

        cardLayoutConexao = new CardLayout();
        painelConexaoCards = new JPanel(cardLayoutConexao);
        painelConexaoCards.setOpaque(false);
        painelConexaoCards.add(construirCartaoLogin(), CARD_LOGIN);
        painelConexaoCards.add(construirCartaoConectado(), CARD_CONECTADO);

        JPanel painelStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        painelStatus.setOpaque(false);
        statusDot = new JLabel("●");
        statusDot.setForeground(Theme.DANGER);
        statusLabel = new JLabel("Desconectado");
        statusLabel.setFont(Theme.FONT_SMALL);
        statusLabel.setForeground(Theme.TEXT_SECONDARY);
        painelStatus.add(statusDot);
        painelStatus.add(statusLabel);

        direita.add(painelConexaoCards);
        direita.add(painelStatus);

        topo.add(marca, BorderLayout.WEST);
        topo.add(direita, BorderLayout.EAST);

        return topo;
    }

    private JComponent construirCartaoLogin() {
        JPanel painel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        painel.setOpaque(false);

        campoUsuarioLogin = criarCampoTexto("Usuário", 120);
        campoSenhaLogin = criarCampoSenha("Senha", 110);
        campoOrgaoLogin = criarCampoTexto("Órgão", 150);

        botaoConectar = new RoundedButton("Conectar", Theme.ACCENT, Theme.ACCENT_HOVER, Color.WHITE);
        botaoConectar.addActionListener(e -> aoClicarConectar());
        campoOrgaoLogin.addActionListener(e -> aoClicarConectar());

        painel.add(rotulado("Usuário", campoUsuarioLogin));
        painel.add(rotulado("Senha", campoSenhaLogin));
        painel.add(rotulado("Órgão", campoOrgaoLogin));
        painel.add(alinhadoInferior(botaoConectar));

        return painel;
    }

    private JComponent construirCartaoConectado() {
        JPanel painel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        painel.setOpaque(false);

        JPanel identificacao = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        identificacao.setOpaque(false);

        JLabel avatar = new JLabel("👤");
        avatar.setFont(new Font("Segoe UI", Font.PLAIN, 18));

        JPanel textoIdentificacao = new JPanel();
        textoIdentificacao.setOpaque(false);
        textoIdentificacao.setLayout(new BoxLayout(textoIdentificacao, BoxLayout.Y_AXIS));

        labelUsuarioConectado = new JLabel("—");
        labelUsuarioConectado.setFont(Theme.FONT_TITLE);
        labelUsuarioConectado.setForeground(Theme.TEXT_PRIMARY);

        labelOrgaoConectado = new JLabel(" ");
        labelOrgaoConectado.setFont(Theme.FONT_SMALL);
        labelOrgaoConectado.setForeground(Theme.TEXT_SECONDARY);

        textoIdentificacao.add(labelUsuarioConectado);
        textoIdentificacao.add(labelOrgaoConectado);

        identificacao.add(avatar);
        identificacao.add(textoIdentificacao);

        botaoDesconectar = new RoundedButton("Desconectar", Theme.BG_ELEVATED, Theme.DANGER, Theme.TEXT_PRIMARY);
        botaoDesconectar.addActionListener(e -> client.desconectar());

        painel.add(identificacao);
        painel.add(botaoDesconectar);

        return painel;
    }

    private JComponent alinhadoInferior(JComponent componente) {
        JPanel painel = new JPanel();
        painel.setOpaque(false);
        painel.setLayout(new BoxLayout(painel, BoxLayout.Y_AXIS));
        JLabel espaco = new JLabel(" ");
        espaco.setFont(Theme.FONT_SMALL);
        painel.add(espaco);
        painel.add(componente);
        return painel;
    }

    private JComponent rotulado(String texto, JComponent campo) {
        JPanel painel = new JPanel();
        painel.setOpaque(false);
        painel.setLayout(new BoxLayout(painel, BoxLayout.Y_AXIS));
        JLabel rotulo = new JLabel(texto);
        rotulo.setFont(Theme.FONT_SMALL);
        rotulo.setForeground(Theme.TEXT_MUTED);
        rotulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        campo.setAlignmentX(Component.LEFT_ALIGNMENT);
        painel.add(rotulo);
        painel.add(campo);
        return painel;
    }

    // =================================================================
    //  PAINEL LATERAL — agora com duas abas: USUÁRIOS e GRUPOS
    // =================================================================
    private JComponent construirPainelLateral() {
        RoundedPanel painel = new RoundedPanel(16, Theme.BG_SURFACE);
        painel.setLayout(new BorderLayout());
        painel.setBorder(new EmptyBorder(16, 16, 16, 16));
        painel.setPreferredSize(new Dimension(270, 10));

        painel.add(construirSeletorAbaLateral(), BorderLayout.NORTH);

        cardLayoutLateral = new CardLayout();
        painelLateralCards = new JPanel(cardLayoutLateral);
        painelLateralCards.setOpaque(false);
        painelLateralCards.setBorder(new EmptyBorder(12, 0, 0, 0));
        painelLateralCards.add(construirSecaoUsuarios(), CARD_ABA_USUARIOS);
        painelLateralCards.add(construirSecaoGrupos(), CARD_ABA_GRUPOS);

        painel.add(painelLateralCards, BorderLayout.CENTER);

        return painel;
    }

    /** Par de abas (pílula) para alternar entre a lista de usuários e a de grupos. */
    private JComponent construirSeletorAbaLateral() {
        RoundedPanel fundo = new RoundedPanel(10, Theme.BG_ELEVATED);
        fundo.setLayout(new GridLayout(1, 2, 4, 0));
        fundo.setBorder(new EmptyBorder(3, 3, 3, 3));
        fundo.setPreferredSize(new Dimension(10, 38));

        abaUsuariosBotao = new AbaLateralBotao("Usuários");
        abaGruposBotao = new AbaLateralBotao("Grupos");

        abaUsuariosBotao.addActionListener(e -> {
            cardLayoutLateral.show(painelLateralCards, CARD_ABA_USUARIOS);
            abaUsuariosBotao.setAtivo(true);
            abaGruposBotao.setAtivo(false);
        });
        abaGruposBotao.addActionListener(e -> {
            cardLayoutLateral.show(painelLateralCards, CARD_ABA_GRUPOS);
            abaGruposBotao.setAtivo(true);
            abaUsuariosBotao.setAtivo(false);
        });

        abaUsuariosBotao.setAtivo(true);
        abaGruposBotao.setAtivo(false);

        fundo.add(abaUsuariosBotao);
        fundo.add(abaGruposBotao);

        return fundo;
    }

    // -----------------------------------------------------------------
    //  Aba USUÁRIOS (conteúdo idêntico ao já existente — apenas movido
    //  para dentro do card correspondente do CardLayout lateral).
    // -----------------------------------------------------------------
    private JComponent construirSecaoUsuarios() {
        JPanel painel = new JPanel(new BorderLayout());
        painel.setOpaque(false);

        JPanel topoLateral = new JPanel(new BorderLayout());
        topoLateral.setOpaque(false);

        JLabel titulo = new JLabel("USUÁRIOS CONECTADOS");
        titulo.setFont(Theme.FONT_SMALL);
        titulo.setForeground(Theme.TEXT_MUTED);
        titulo.setBorder(new EmptyBorder(0, 4, 8, 0));

        campoBusca = criarCampoTexto("Buscar destinatário...", 0);
        campoBusca.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_SUBTLE, 1, true),
                new EmptyBorder(6, 10, 6, 32)
        ));
        campoBusca.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrarUsuarios(); }
            public void removeUpdate(DocumentEvent e) { filtrarUsuarios(); }
            public void changedUpdate(DocumentEvent e) { filtrarUsuarios(); }
        });

        topoLateral.add(titulo, BorderLayout.NORTH);
        topoLateral.add(criarCampoComIconeLupa(campoBusca), BorderLayout.SOUTH);
        topoLateral.setBorder(new EmptyBorder(0, 0, 12, 0));

        modeloListaUsuarios = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloListaUsuarios);
        listaUsuarios.setBackground(Theme.BG_SURFACE);
        listaUsuarios.setForeground(Theme.TEXT_PRIMARY);
        listaUsuarios.setFont(Theme.FONT_BODY);
        listaUsuarios.setSelectionBackground(Theme.ACCENT_SOFT);
        listaUsuarios.setSelectionForeground(Theme.TEXT_PRIMARY);
        listaUsuarios.setFixedCellHeight(52);
        listaUsuarios.setBorder(null);
        listaUsuarios.setCellRenderer(new UsuarioCellRenderer());

        // Um único clique seleciona o usuário e ABRE a conversa com ele
        // (o histórico é solicitado dentro de abrirConversa()).
        listaUsuarios.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && listaUsuarios.getSelectedValue() != null) {
                abrirConversa(listaUsuarios.getSelectedValue().nome, false);
            }
        });

        JScrollPane scroll = new JScrollPane(listaUsuarios);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG_SURFACE);
        estilizarBarraRolagem(scroll);

        painel.add(topoLateral, BorderLayout.NORTH);
        painel.add(scroll, BorderLayout.CENTER);

        // Usuários de exemplo apenas para visualização do layout.
        // TODO (CONEXÃO FUTURA): remover estes itens de exemplo — a lista
        // real será populada via onListaUsuariosAtualizada(), com dados
        // (nome + órgão) enviados pelo servidor.
        todosUsuarios.add(new Client.UsuarioInfo("ana.silva", "Ministério da Fazenda"));
        todosUsuarios.add(new Client.UsuarioInfo("carlos.souza", "Tribunal de Contas da União"));
        todosUsuarios.add(new Client.UsuarioInfo("fabio.tec", "Secretaria de Tecnologia"));
        filtrarUsuarios();

        return painel;
    }

    /** Filtra a lista de usuários conforme o texto digitado no campo de busca (nome ou órgão). */
    private void filtrarUsuarios() {
        String termo = campoBusca.getText().trim().toLowerCase();
        Client.UsuarioInfo selecionadoAntes = listaUsuarios.getSelectedValue();

        modeloListaUsuarios.clear();
        for (Client.UsuarioInfo usuario : todosUsuarios) {
            boolean corresponde = termo.isEmpty()
                    || usuario.nome.toLowerCase().contains(termo)
                    || usuario.orgao.toLowerCase().contains(termo);
            if (corresponde) {
                modeloListaUsuarios.addElement(usuario);
            }
        }

        if (selecionadoAntes != null && modeloListaUsuarios.contains(selecionadoAntes)) {
            listaUsuarios.setSelectedValue(selecionadoAntes, false);
        }
    }

    // -----------------------------------------------------------------
    //  Aba GRUPOS (nova) — busca, botões de criar/entrar e a lista.
    // -----------------------------------------------------------------
    private JComponent construirSecaoGrupos() {
        JPanel painel = new JPanel(new BorderLayout());
        painel.setOpaque(false);

        JPanel topoLateral = new JPanel(new BorderLayout());
        topoLateral.setOpaque(false);
        topoLateral.setBorder(new EmptyBorder(0, 0, 12, 0));

        JLabel titulo = new JLabel("MEUS GRUPOS");
        titulo.setFont(Theme.FONT_SMALL);
        titulo.setForeground(Theme.TEXT_MUTED);
        titulo.setBorder(new EmptyBorder(0, 4, 8, 0));

        campoBuscaGrupos = criarCampoTexto("Buscar grupo...", 0);
        campoBuscaGrupos.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_SUBTLE, 1, true),
                new EmptyBorder(6, 10, 6, 32)
        ));
        campoBuscaGrupos.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrarGrupos(); }
            public void removeUpdate(DocumentEvent e) { filtrarGrupos(); }
            public void changedUpdate(DocumentEvent e) { filtrarGrupos(); }
        });

        JPanel botoesGrupo = new JPanel(new GridLayout(1, 2, 8, 0));
        botoesGrupo.setOpaque(false);
        botoesGrupo.setBorder(new EmptyBorder(10, 0, 0, 0));

        RoundedButton botaoCriarGrupo = new RoundedButton("+ Criar", Theme.ACCENT, Theme.ACCENT_HOVER, Color.WHITE);
        botaoCriarGrupo.setBorder(new EmptyBorder(6, 10, 6, 10));
        botaoCriarGrupo.addActionListener(e -> aoClicarCriarGrupo());

        RoundedButton botaoEntrarGrupo = new RoundedButton("Entrar", Theme.BG_ELEVATED, Theme.BORDER_SUBTLE, Theme.TEXT_PRIMARY);
        botaoEntrarGrupo.setBorder(new EmptyBorder(6, 10, 6, 10));
        botaoEntrarGrupo.addActionListener(e -> aoClicarEntrarGrupo());

        botoesGrupo.add(botaoCriarGrupo);
        botoesGrupo.add(botaoEntrarGrupo);

        topoLateral.add(titulo, BorderLayout.NORTH);
        topoLateral.add(criarCampoComIconeLupa(campoBuscaGrupos), BorderLayout.CENTER);
        topoLateral.add(botoesGrupo, BorderLayout.SOUTH);

        modeloListaGrupos = new DefaultListModel<>();
        listaGrupos = new JList<>(modeloListaGrupos);
        listaGrupos.setBackground(Theme.BG_SURFACE);
        listaGrupos.setForeground(Theme.TEXT_PRIMARY);
        listaGrupos.setFont(Theme.FONT_BODY);
        listaGrupos.setSelectionBackground(Theme.ACCENT_SOFT);
        listaGrupos.setSelectionForeground(Theme.TEXT_PRIMARY);
        listaGrupos.setFixedCellHeight(40);
        listaGrupos.setBorder(null);
        listaGrupos.setCellRenderer(new GrupoCellRenderer());

        // Um único clique seleciona o grupo e ABRE a conversa do grupo
        // (o histórico é solicitado dentro de abrirConversa()).
        listaGrupos.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && listaGrupos.getSelectedValue() != null) {
                abrirConversa(listaGrupos.getSelectedValue(), true);
            }
        });

        JScrollPane scroll = new JScrollPane(listaGrupos);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG_SURFACE);
        estilizarBarraRolagem(scroll);

        painel.add(topoLateral, BorderLayout.NORTH);
        painel.add(scroll, BorderLayout.CENTER);

        return painel;
    }

    private void aoClicarCriarGrupo() {
        if (!client.isConectado()) {
            JOptionPane.showMessageDialog(this,
                    "Conecte-se ao servidor antes de criar um grupo.",
                    "Não conectado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String nomeGrupo = JOptionPane.showInputDialog(this,
                "Nome do novo grupo:", "Criar grupo", JOptionPane.PLAIN_MESSAGE);

        if (nomeGrupo != null && !nomeGrupo.trim().isEmpty()) {
            // TODO (CONEXÃO FUTURA): client.criarGrupo() hoje apenas
            // simula a criação localmente — ver Client.java.
            client.criarGrupo(nomeGrupo.trim());
        }
    }

    private void aoClicarEntrarGrupo() {
        if (!client.isConectado()) {
            JOptionPane.showMessageDialog(this,
                    "Conecte-se ao servidor antes de entrar em um grupo.",
                    "Não conectado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String identificador = JOptionPane.showInputDialog(this,
                "Nome ou código de convite do grupo:", "Entrar em grupo", JOptionPane.PLAIN_MESSAGE);

        if (identificador != null && !identificador.trim().isEmpty()) {
            // TODO (CONEXÃO FUTURA): client.entrarGrupo() hoje apenas
            // simula a entrada localmente — ver Client.java.
            client.entrarGrupo(identificador.trim());
        }
    }

    /** Filtra a lista de grupos conforme o texto digitado no campo de busca. */
    private void filtrarGrupos() {
        String termo = campoBuscaGrupos.getText().trim().toLowerCase();
        String selecionadoAntes = listaGrupos.getSelectedValue();

        modeloListaGrupos.clear();
        for (String grupo : todosGrupos) {
            if (termo.isEmpty() || grupo.toLowerCase().contains(termo)) {
                modeloListaGrupos.addElement(grupo);
            }
        }

        if (selecionadoAntes != null && modeloListaGrupos.contains(selecionadoAntes)) {
            listaGrupos.setSelectedValue(selecionadoAntes, false);
        }
    }

    /** Sobrepõe um ícone de lupa (desenhado) sobre um campo de busca, sem libs externas. */
    private JLayeredPane criarCampoComIconeLupa(JTextField campo) {
        JLayeredPane camadas = new JLayeredPane();
        camadas.setPreferredSize(new Dimension(10, 34));
        camadas.setOpaque(false);

        JLabel icone = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawOval(3, 3, 10, 10);
                g2.drawLine(12, 12, 16, 16);
                g2.dispose();
            }
        };

        camadas.add(campo, Integer.valueOf(0));
        camadas.add(icone, Integer.valueOf(1));

        camadas.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                campo.setBounds(0, 0, camadas.getWidth(), camadas.getHeight());
                icone.setBounds(camadas.getWidth() - 26, (camadas.getHeight() - 18) / 2, 18, 18);
            }
        });

        return camadas;
    }

    // =================================================================
    //  PAINEL CENTRAL — cabeçalho da conversa + mensagens + entrada
    // =================================================================
    private JComponent construirPainelChat() {
        RoundedPanel painel = new RoundedPanel(16, Theme.BG_SURFACE);
        painel.setLayout(new BorderLayout());
        painel.setBorder(new EmptyBorder(16, 16, 16, 16));

        tituloConversa = new JLabel("Selecione um usuário ou grupo para iniciar uma conversa");
        tituloConversa.setFont(Theme.FONT_TITLE);
        tituloConversa.setForeground(Theme.TEXT_PRIMARY);
        tituloConversa.setBorder(new EmptyBorder(0, 4, 12, 0));

        areaMensagens = new JTextPane();
        areaMensagens.setEditable(false);
        areaMensagens.setBackground(Theme.BG_SURFACE);
        areaMensagens.setForeground(Theme.TEXT_PRIMARY);
        areaMensagens.setFont(Theme.FONT_BODY);
        areaMensagens.setBorder(new EmptyBorder(4, 8, 4, 8));
        documentoMensagens = areaMensagens.getStyledDocument();

        JScrollPane scrollMensagens = new JScrollPane(areaMensagens);
        scrollMensagens.setBorder(null);
        scrollMensagens.getViewport().setBackground(Theme.BG_SURFACE);
        estilizarBarraRolagem(scrollMensagens);

        painel.add(tituloConversa, BorderLayout.NORTH);
        painel.add(scrollMensagens, BorderLayout.CENTER);
        painel.add(construirPainelEntrada(), BorderLayout.SOUTH);

        renderizarPlaceholderSemConversa();

        return painel;
    }

    private JComponent construirPainelEntrada() {
        JPanel painel = new JPanel(new BorderLayout(10, 0));
        painel.setOpaque(false);
        painel.setBorder(new EmptyBorder(12, 0, 0, 0));

        campoMensagem = criarCampoTexto("Digite sua mensagem...", 0);
        campoMensagem.setPreferredSize(new Dimension(10, 44));
        campoMensagem.addActionListener(e -> aoClicarEnviar());

        botaoAnexar = new BotaoAnexo(Theme.BG_ELEVATED, Theme.BORDER_SUBTLE, Theme.TEXT_PRIMARY);
        botaoAnexar.setPreferredSize(new Dimension(46, 44));
        botaoAnexar.addActionListener(e -> aoClicarAnexar());

        botaoEnviar = new RoundedButton("Enviar  ➤", Theme.ACCENT, Theme.ACCENT_HOVER, Color.WHITE);
        botaoEnviar.setPreferredSize(new Dimension(112, 44));
        botaoEnviar.addActionListener(e -> aoClicarEnviar());

        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        direita.setOpaque(false);
        direita.add(botaoAnexar);
        direita.add(botaoEnviar);

        painel.add(campoMensagem, BorderLayout.CENTER);
        painel.add(direita, BorderLayout.EAST);

        return painel;
    }

    // =================================================================
    //  AÇÕES DA INTERFACE — apenas chamam o Client; nenhuma lógica de
    //  rede acontece aqui.
    // =================================================================

    private void aoClicarConectar() {
        String usuario = campoUsuarioLogin.getText().trim();
        char[] senha = campoSenhaLogin.getPassword();
        String orgao = campoOrgaoLogin.getText().trim();

        if (usuario.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Informe seu usuário antes de conectar.",
                    "Campo obrigatório", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (senha.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "Informe sua senha antes de conectar.",
                    "Campo obrigatório", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (orgao.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Informe o órgão ao qual você está vinculado.",
                    "Campo obrigatório", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Host/porta do servidor. Ajuste aqui caso o servidor esteja rodando
        // em outra máquina (ex.: "servidor.nexus.gov") ou outra porta.
        // Por padrão aponta para um servidor rodando localmente (ServerMain),
        // que usa a porta 5000 quando nenhuma é informada nos argumentos.

        checkDomainIdAndPort();

        client.conectarServidor(this.domainId, this.port, usuario, senha, orgao);
    }

    private void checkDomainIdAndPort() {
        this.domainId = "localhost";
        this.port = 5000;
        if (commandLineArgs.length > 0) {
            try {
                this.port = Integer.parseInt(commandLineArgs[0]);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                        "Porta inválida. Use um número inteiro.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

        if (commandLineArgs.length > 1) {
            this.domainId = commandLineArgs[1];
        }
    }

    private void aoClicarEnviar() {
        String mensagem = campoMensagem.getText().trim();

        if (!client.isConectado()) {
            JOptionPane.showMessageDialog(this,
                    "Conecte-se ao servidor antes de enviar mensagens.",
                    "Não conectado", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (conversaAtual == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecione um usuário ou grupo à esquerda para iniciar a conversa.",
                    "Nenhuma conversa selecionada", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (mensagem.isEmpty()) {
            return;
        }

        client.enviarMensagem(conversaAtual, mensagem, conversaAtualEhGrupo);
        registrarEExibirMensagem(conversaAtual, conversaAtualEhGrupo,
                client.getUsuarioAtual(), mensagem, true, false);
        campoMensagem.setText("");
    }

    private void aoClicarAnexar() {
        if (!client.isConectado()) {
            JOptionPane.showMessageDialog(this,
                    "Conecte-se ao servidor antes de enviar arquivos.",
                    "Não conectado", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (conversaAtual == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecione um usuário ou grupo à esquerda antes de anexar um arquivo.",
                    "Nenhuma conversa selecionada", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File arquivo = fileManager.selecionarArquivo(this);
        if (arquivo == null) {
            return; // usuário cancelou a seleção
        }

        // A confirmação (com nome, tamanho e destinatário) acontece dentro
        // do FileManager; só registramos na conversa se o envio for confirmado.
        boolean enviado = fileManager.confirmarEEnviarArquivo(this, arquivo, client, conversaAtual);
        if (enviado) {
            // Usa o caminho ABSOLUTO para que o cartão de arquivo consiga localizar
            // o arquivo original no disco do remetente.
            registrarEExibirMensagem(conversaAtual, conversaAtualEhGrupo,
                    client.getUsuarioAtual(), arquivo.getAbsolutePath(), true, true);
        }
    }

    // =================================================================
    //  GERENCIAMENTO DE CONVERSAS (pessoais e de grupo)
    // =================================================================

    /**
     * Abre (ou foca) a conversa com o usuário ou grupo selecionado e
     * dispara o carregamento do histórico dessa conversa junto ao
     * servidor (hoje simulado — ver Client.solicitarHistorico).
     */
    private void abrirConversa(String identificador, boolean grupo) {
        conversaAtual = identificador;
        conversaAtualEhGrupo = grupo;
        tituloConversa.setText((grupo ? "Grupo: " : "Conversa com ") + identificador);

        (grupo ? gruposComNaoLidas : usuariosComNaoLidas).remove(identificador);
        listaUsuarios.repaint();
        listaGrupos.repaint();

        carregarHistoricoConversa(identificador, grupo);
        campoMensagem.requestFocusInWindow();
    }

    /**
     * Garante que o histórico da conversa seja solicitado ao servidor na
     * primeira vez em que ela é aberta nesta sessão, e então (re)desenha
     * a área de mensagens. Enquanto o histórico não chega, a conversa é
     * exibida vazia — a atualização acontece em onHistoricoCarregado().
     */
    private void carregarHistoricoConversa(String identificador, boolean grupo) {
        String chave = chaveConversa(identificador, grupo);
        if (!conversas.containsKey(chave)) {
            conversas.put(chave, new ArrayList<>()); // evita solicitar novamente nesta sessão
            client.solicitarHistorico(identificador, grupo);
        }
        redesenharConversaAtual();
    }

    /** Chave única por conversa, evitando colisão entre usuário e grupo de mesmo nome. */
    private String chaveConversa(String identificador, boolean grupo) {
        return (grupo ? "GRUPO:" : "USUARIO:") + identificador;
    }

    /** Adiciona a mensagem ao histórico da conversa e, se ela estiver aberta, exibe imediatamente. */
    private void registrarEExibirMensagem(String identificadorConversa, boolean grupo, String remetente,
                                           String texto, boolean propria, boolean arquivo) {
        String chave = chaveConversa(identificadorConversa, grupo);
        List<MensagemRegistrada> historico = conversas.computeIfAbsent(chave, k -> new ArrayList<>());
        historico.add(new MensagemRegistrada(remetente, texto, propria, arquivo));

        boolean conversaAberta = identificadorConversa.equals(conversaAtual) && grupo == conversaAtualEhGrupo;

        if (conversaAberta) {
            renderizarMensagem(historico.get(historico.size() - 1));
        } else if (!propria) {
            (grupo ? gruposComNaoLidas : usuariosComNaoLidas).add(identificadorConversa);
            listaUsuarios.repaint();
            listaGrupos.repaint();
        }
    }

    /** Redesenha do zero a área de mensagens a partir do histórico salvo da conversa ativa. */
    private void redesenharConversaAtual() {
        documentoMensagens = new DefaultStyledDocument();
        areaMensagens.setStyledDocument(documentoMensagens);

        List<MensagemRegistrada> historico = conversas.get(chaveConversa(conversaAtual, conversaAtualEhGrupo));
        if (historico == null || historico.isEmpty()) {
            adicionarLinhaSistema("Nenhuma mensagem ainda. Diga olá" +
                    (conversaAtualEhGrupo ? " no grupo " : " para ") + conversaAtual + "!");
            return;
        }
        for (MensagemRegistrada m : historico) {
            renderizarMensagem(m);
        }
    }

    private void renderizarPlaceholderSemConversa() {
        try {
            SimpleAttributeSet estilo = new SimpleAttributeSet();
            StyleConstants.setForeground(estilo, Theme.TEXT_MUTED);
            StyleConstants.setItalic(estilo, true);
            documentoMensagens.insertString(0,
                    "Selecione um usuário ou grupo na lista à esquerda para iniciar uma conversa.",
                    estilo);
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void renderizarMensagem(MensagemRegistrada m) {
        try {
            SimpleAttributeSet estiloRemetente = new SimpleAttributeSet();
            StyleConstants.setForeground(estiloRemetente, m.propria ? Theme.ACCENT : Theme.SUCCESS);
            StyleConstants.setBold(estiloRemetente, true);

            SimpleAttributeSet estiloHora = new SimpleAttributeSet();
            StyleConstants.setForeground(estiloHora, Theme.TEXT_MUTED);
            StyleConstants.setFontSize(estiloHora, 11);

            documentoMensagens.insertString(documentoMensagens.getLength(), m.remetente + "  ", estiloRemetente);
            documentoMensagens.insertString(documentoMensagens.getLength(), m.hora + "\n", estiloHora);

            if (m.arquivo) {
                // Insere o cartão interativo diretamente no fluxo de texto do JTextPane.
                File arquivoResolvido = fileManager.resolverArquivo(m.texto);
                PainelCartaoArquivo cartao = new PainelCartaoArquivo(arquivoResolvido, m.texto, m.propria);
                SimpleAttributeSet estiloComp = new SimpleAttributeSet();
                StyleConstants.setComponent(estiloComp, cartao);
                // O espaço em branco é substituído visualmente pelo componente.
                documentoMensagens.insertString(documentoMensagens.getLength(), " ", estiloComp);
                documentoMensagens.insertString(documentoMensagens.getLength(), "\n\n", new SimpleAttributeSet());
            } else {
                SimpleAttributeSet estiloTexto = new SimpleAttributeSet();
                StyleConstants.setForeground(estiloTexto, Theme.TEXT_PRIMARY);
                documentoMensagens.insertString(documentoMensagens.getLength(), m.texto + "\n\n", estiloTexto);
            }

            areaMensagens.setCaretPosition(documentoMensagens.getLength());
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void adicionarLinhaSistema(String texto) {
        try {
            SimpleAttributeSet estilo = new SimpleAttributeSet();
            StyleConstants.setForeground(estilo, Theme.TEXT_MUTED);
            StyleConstants.setItalic(estilo, true);
            documentoMensagens.insertString(documentoMensagens.getLength(), "• " + texto + "\n\n", estilo);
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    // =================================================================
    //  IMPLEMENTAÇÃO DE Client.MessageListener
    // =================================================================

    @Override
    public void onMensagemRecebida(String remetente, String mensagem) {
        SwingUtilities.invokeLater(() ->
                registrarEExibirMensagem(remetente, false, remetente, mensagem, false, false));
    }

    @Override
    public void onMensagemGrupoRecebida(String identificadorGrupo, String remetente, String mensagem) {
        SwingUtilities.invokeLater(() ->
                registrarEExibirMensagem(identificadorGrupo, true, remetente, mensagem, false, false));
    }

    @Override
    public void onStatusConexaoAlterado(boolean conectado, String detalhes) {
        SwingUtilities.invokeLater(() -> {
            statusDot.setForeground(conectado ? Theme.SUCCESS : Theme.DANGER);
            statusLabel.setText(detalhes != null ? detalhes : (conectado ? "Conectado" : "Desconectado"));

            if (conectado) {
                labelUsuarioConectado.setText(client.getUsuarioAtual());
                labelOrgaoConectado.setText(client.getOrgaoAtual());
                cardLayoutConexao.show(painelConexaoCards, CARD_CONECTADO);
                campoSenhaLogin.setText("");
            } else {
                cardLayoutConexao.show(painelConexaoCards, CARD_LOGIN);
            }
        });
    }

    @Override
    public void onListaUsuariosAtualizada(List<Client.UsuarioInfo> usuarios) {
        SwingUtilities.invokeLater(() -> {
            todosUsuarios.clear();
            todosUsuarios.addAll(usuarios);
            filtrarUsuarios();
        });
    }

    @Override
    public void onListaGruposAtualizada(List<String> grupos) {
        SwingUtilities.invokeLater(() -> {
            todosGrupos.clear();
            todosGrupos.addAll(grupos);
            filtrarGrupos();
        });
    }

    @Override
    public void onArquivoRecebido(String remetente, String nomeArquivo) {
        SwingUtilities.invokeLater(() ->
                registrarEExibirMensagem(remetente, false, remetente, nomeArquivo, false, true));
    }

    @Override
    public void onArquivoGrupoRecebido(String identificadorGrupo, String remetente, String nomeArquivo) {
        SwingUtilities.invokeLater(() ->
                registrarEExibirMensagem(identificadorGrupo, true, remetente, nomeArquivo, false, true));
    }

    /**
     * Recebe o histórico de uma conversa (pessoal ou de grupo) vindo do
     * servidor. Substitui o histórico local (que até então estava vazio,
     * apenas reservando a solicitação) e redesenha a tela se a conversa
     * ainda estiver aberta.
     */
    @Override
    public void onHistoricoCarregado(String identificadorConversa, boolean grupo,
                                      List<Client.MensagemHistorico> mensagens) {
        SwingUtilities.invokeLater(() -> {
            String chave = chaveConversa(identificadorConversa, grupo);

            List<MensagemRegistrada> historico = new ArrayList<>();
            for (Client.MensagemHistorico m : mensagens) {
                boolean propria = m.remetente.equals(client.getUsuarioAtual());
                historico.add(new MensagemRegistrada(m.remetente, m.texto, propria, m.arquivo, m.hora));
            }
            conversas.put(chave, historico);

            boolean conversaAberta = identificadorConversa.equals(conversaAtual) && grupo == conversaAtualEhGrupo;
            if (conversaAberta) {
                redesenharConversaAtual();
            }
        });
    }

    // =================================================================
    //  UTILITÁRIOS DE INTERFACE
    // =================================================================

    private JTextField criarCampoTexto(String placeholder, int largura) {
        JTextField campo = new JTextField();
        aplicarEstiloCampo(campo, largura);
        return campo;
    }

    private JPasswordField criarCampoSenha(String placeholder, int largura) {
        JPasswordField campo = new JPasswordField();
        campo.setEchoChar('•');
        aplicarEstiloCampo(campo, largura);
        return campo;
    }

    /**
     * Aplica o estilo visual (cores do tema escuro) a um campo de texto,
     * garantindo explicitamente cor de fundo, texto, cursor e seleção —
     * isso evita o bug de "texto invisível" que ocorre em alguns Look
     * and Feels nativos que ignoram cores customizadas.
     */
    private void aplicarEstiloCampo(JTextField campo, int largura) {
        if (largura > 0) {
            campo.setPreferredSize(new Dimension(largura, 34));
        }
        campo.setBackground(Theme.BG_INPUT);
        campo.setForeground(Theme.TEXT_PRIMARY);
        campo.setCaretColor(Theme.TEXT_PRIMARY);
        campo.setSelectionColor(Theme.ACCENT_SOFT);
        campo.setSelectedTextColor(Theme.TEXT_PRIMARY);
        campo.setFont(Theme.FONT_BODY);
        campo.setOpaque(true);
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_SUBTLE, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
    }

    private void estilizarBarraRolagem(JScrollPane scroll) {
        scroll.getVerticalScrollBar().setBackground(Theme.BG_SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
    }

    // =================================================================
    //  COMPONENTES CUSTOMIZADOS
    // =================================================================

    /** Painel com fundo em cantos arredondados. */
    static class RoundedPanel extends JPanel {
        private final int raio;
        private final Color corFundo;

        RoundedPanel(int raio, Color corFundo) {
            this.raio = raio;
            this.corFundo = corFundo;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(corFundo);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), raio, raio));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Botão minimalista com cantos arredondados e efeito de hover. */
    static class RoundedButton extends JButton {
        private final Color corBase;
        private final Color corHover;
        protected boolean sobreOBotao = false;

        RoundedButton(String texto, Color corBase, Color corHover, Color corTexto) {
            super(texto);
            this.corBase = corBase;
            this.corHover = corHover;

            setForeground(corTexto);
            setFont(Theme.FONT_BODY);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 18, 8, 18));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    sobreOBotao = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    sobreOBotao = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(sobreOBotao ? corHover : corBase);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Botão de aba lateral (pílula "Usuários" / "Grupos"), com estado
     * ativo/inativo — usa a mesma cor de destaque (ACCENT) do restante
     * da identidade visual quando ativo.
     */
    static class AbaLateralBotao extends JButton {
        private boolean ativo;

        AbaLateralBotao(String texto) {
            super(texto);
            setFont(Theme.FONT_SMALL);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(6, 12, 6, 12));
        }

        void setAtivo(boolean ativo) {
            this.ativo = ativo;
            setForeground(ativo ? Color.WHITE : Theme.TEXT_SECONDARY);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(ativo ? Theme.ACCENT : Theme.BG_ELEVATED);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Botão de anexar arquivo com um ícone de CLIPE desenhado
     * vetorialmente (Graphics2D), em vez de depender de um caractere
     * emoji — garante que o ícone sempre apareça corretamente.
     */
    static class BotaoAnexo extends RoundedButton {
        BotaoAnexo(Color corBase, Color corHover, Color corIcone) {
            super("", corBase, corHover, corIcone);
            setToolTipText("Enviar arquivo");
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getForeground());
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            g2.translate(cx, cy);
            g2.rotate(Math.toRadians(45));

            RoundRectangle2D corpo = new RoundRectangle2D.Float(-4.5f, -11f, 9f, 22f, 9f, 9f);
            g2.draw(corpo);

            g2.dispose();
        }
    }

    /** Renderizador de células para a lista de usuários (status + nome + órgão + indicador de não lida). */
    class UsuarioCellRenderer extends JPanel implements ListCellRenderer<Client.UsuarioInfo> {
        private final JLabel bolinhaStatus = new JLabel("●");
        private final JLabel nome = new JLabel();
        private final JLabel orgao = new JLabel();
        private final JLabel bolinhaNaoLida = new JLabel("●");

        UsuarioCellRenderer() {
            setLayout(new BorderLayout());
            setOpaque(true);
            setBorder(new EmptyBorder(2, 6, 2, 10));

            JPanel textos = new JPanel();
            textos.setOpaque(false);
            textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
            nome.setFont(Theme.FONT_BODY);
            orgao.setFont(Theme.FONT_SMALL);

            JPanel esquerda = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            esquerda.setOpaque(false);
            bolinhaStatus.setForeground(Theme.SUCCESS);
            bolinhaStatus.setFont(new Font("Segoe UI", Font.PLAIN, 10));

            textos.add(nome);
            textos.add(orgao);
            esquerda.add(bolinhaStatus);
            esquerda.add(textos);

            bolinhaNaoLida.setForeground(Theme.ACCENT);
            bolinhaNaoLida.setFont(new Font("Segoe UI", Font.PLAIN, 10));

            add(esquerda, BorderLayout.WEST);
            add(bolinhaNaoLida, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Client.UsuarioInfo> list, Client.UsuarioInfo value,
                                                        int index, boolean isSelected, boolean cellHasFocus) {
            nome.setText(value.nome);
            orgao.setText(value.orgao);
            boolean ativa = !conversaAtualEhGrupo && value.nome.equals(conversaAtual);
            setBackground(isSelected || ativa ? Theme.ACCENT_SOFT : Theme.BG_SURFACE);
            nome.setForeground(Theme.TEXT_PRIMARY);
            orgao.setForeground(Theme.TEXT_SECONDARY);
            bolinhaNaoLida.setVisible(usuariosComNaoLidas.contains(value.nome));
            return this;
        }
    }

    /**
     * Renderizador de células para a lista de grupos — mesmo padrão
     * visual da lista de usuários, trocando o indicador de status
     * "online" (círculo verde) por um ícone de grupo ("#").
     */
    class GrupoCellRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel iconeGrupo = new JLabel("#");
        private final JLabel nome = new JLabel();
        private final JLabel bolinhaNaoLida = new JLabel("●");

        GrupoCellRenderer() {
            setLayout(new BorderLayout());
            setOpaque(true);
            setBorder(new EmptyBorder(0, 6, 0, 10));

            JPanel esquerda = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
            esquerda.setOpaque(false);
            iconeGrupo.setForeground(Theme.TEXT_MUTED);
            iconeGrupo.setFont(new Font("Segoe UI", Font.BOLD, 13));
            nome.setFont(Theme.FONT_BODY);
            esquerda.add(iconeGrupo);
            esquerda.add(nome);

            bolinhaNaoLida.setForeground(Theme.ACCENT);
            bolinhaNaoLida.setFont(new Font("Segoe UI", Font.PLAIN, 10));

            add(esquerda, BorderLayout.WEST);
            add(bolinhaNaoLida, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                                                        int index, boolean isSelected, boolean cellHasFocus) {
            nome.setText(value);
            boolean ativa = conversaAtualEhGrupo && value.equals(conversaAtual);
            setBackground(isSelected || ativa ? Theme.ACCENT_SOFT : Theme.BG_SURFACE);
            nome.setForeground(Theme.TEXT_PRIMARY);
            bolinhaNaoLida.setVisible(gruposComNaoLidas.contains(value));
            return this;
        }
    }

    // =================================================================
    //  CARTÃO INTERATIVO DE ARQUIVO — exibido no chat no lugar do
    //  texto estático antigo ("📎 Arquivo enviado: nome").
    // =================================================================

    /**
     * Painel visual embutido diretamente no JTextPane via StyleConstants.setComponent.
     * Exibe o nome do arquivo, o tamanho em disco e três botões de ação:
     * "Abrir" (no app padrão do SO), "Salvar Como..." e "Pasta" (abre o
     * gerenciador de arquivos na pasta que contém o arquivo).
     */
    class PainelCartaoArquivo extends JPanel {
        private final Color corFundo;
        private final Color corBorda;

        PainelCartaoArquivo(File arquivo, String textoOriginal, boolean propria) {
            corFundo = propria ? Theme.ACCENT_SOFT : Theme.BG_ELEVATED;
            corBorda = propria ? Theme.ACCENT     : Theme.BORDER_SUBTLE;

            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(10, 14, 10, 14));
            setPreferredSize(new Dimension(420, 64));
            setMaximumSize(new Dimension(420, 64));

            // --- Ícone de clipe (desenhado vetorialmente) ---
            JLabel iconeClipe = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.ACCENT);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.translate(getWidth() / 2, getHeight() / 2);
                    g2.rotate(Math.toRadians(45));
                    g2.draw(new RoundRectangle2D.Float(-4f, -10f, 8f, 20f, 8f, 8f));
                    g2.dispose();
                }
            };
            iconeClipe.setPreferredSize(new Dimension(24, 24));

            // --- Nome e tamanho do arquivo ---
            String nomeExibido = arquivo.getName();
            if (nomeExibido.length() > 36) {
                nomeExibido = nomeExibido.substring(0, 33) + "...";
            }

            String tamanhoTexto;
            if (arquivo.exists()) {
                long bytes = arquivo.length();
                if (bytes < 1024)            tamanhoTexto = bytes + " B";
                else if (bytes < 1024*1024)  tamanhoTexto = (bytes / 1024) + " KB";
                else                         tamanhoTexto = String.format("%.1f MB", bytes / (1024.0*1024));
            } else {
                tamanhoTexto = "Arquivo recebido";
            }

            JLabel lbNome = new JLabel(nomeExibido);
            lbNome.setFont(Theme.FONT_BODY);
            lbNome.setForeground(Theme.TEXT_PRIMARY);

            JLabel lbTamanho = new JLabel(tamanhoTexto);
            lbTamanho.setFont(Theme.FONT_SMALL);
            lbTamanho.setForeground(Theme.TEXT_MUTED);

            JPanel textos = new JPanel();
            textos.setOpaque(false);
            textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
            textos.add(lbNome);
            textos.add(lbTamanho);

            JPanel esquerda = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            esquerda.setOpaque(false);
            esquerda.add(iconeClipe);
            esquerda.add(textos);

            // --- Botões de ação ---
            RoundedButton btnAbrir = new RoundedButton("Abrir", Theme.ACCENT, Theme.ACCENT_HOVER, Color.WHITE);
            btnAbrir.setFont(Theme.FONT_SMALL);
            btnAbrir.setBorder(new EmptyBorder(4, 10, 4, 10));
            btnAbrir.setToolTipText("Abrir arquivo no aplicativo padrão");
            btnAbrir.addActionListener(e ->
                    fileManager.abrirArquivo(SwingUtilities.getWindowAncestor(PainelCartaoArquivo.this), arquivo));

            RoundedButton btnSalvar = new RoundedButton("Salvar", Theme.BG_ELEVATED, Theme.BORDER_SUBTLE, Theme.TEXT_PRIMARY);
            btnSalvar.setFont(Theme.FONT_SMALL);
            btnSalvar.setBorder(new EmptyBorder(4, 10, 4, 10));
            btnSalvar.setToolTipText("Salvar cópia do arquivo em outro local");
            btnSalvar.addActionListener(e ->
                    fileManager.salvarComo(SwingUtilities.getWindowAncestor(PainelCartaoArquivo.this), arquivo));

            RoundedButton btnPasta = new RoundedButton("Pasta", Theme.BG_ELEVATED, Theme.BORDER_SUBTLE, Theme.TEXT_PRIMARY);
            btnPasta.setFont(Theme.FONT_SMALL);
            btnPasta.setBorder(new EmptyBorder(4, 10, 4, 10));
            btnPasta.setToolTipText("Abrir pasta que contém o arquivo");
            btnPasta.addActionListener(e ->
                    fileManager.abrirPasta(SwingUtilities.getWindowAncestor(PainelCartaoArquivo.this), arquivo));

            JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            botoes.setOpaque(false);
            botoes.add(btnAbrir);
            botoes.add(btnSalvar);
            botoes.add(btnPasta);

            add(esquerda, BorderLayout.WEST);
            add(botoes, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Fundo arredondado
            g2.setColor(corFundo);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            // Borda arredondada
            g2.setColor(corBorda);
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new RoundRectangle2D.Float(0.6f, 0.6f, getWidth() - 1.2f, getHeight() - 1.2f, 10, 10));
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
