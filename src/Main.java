import javax.swing.*;

/**
 * Main.java
 * -----------------------------------------------------------------------
 * Ponto de entrada da aplicação cliente do sistema de comunicação
 * corporativa (plataforma nacional fictícia "NEXUS.gov").
 * -----------------------------------------------------------------------
 */
public class Main {

    public static void main(String[] args) {

        aplicarTemaEscuroGlobal();

        SwingUtilities.invokeLater(() -> {
            Client client = new Client();
            ChatWindow janela = new ChatWindow(client, args);
            client.setListener(janela);
            janela.setLocationRelativeTo(null);
            janela.setVisible(true);
        });
    }

    /**
     * Aplica um tema escuro global usando apenas UIManager, sem
     * dependências externas.
     *
     * IMPORTANTE (correção de bug): usamos o Look and Feel
     * "cross-platform" (Metal), em vez do Look and Feel NATIVO do
     * sistema operacional. Em várias distribuições Linux, o L&F nativo
     * (GTK) usa um mecanismo de pintura ("Synth") que IGNORA as cores
     * definidas manualmente via setForeground()/setBackground() em
     * componentes como JTextField — é exatamente isso que causava o
     * texto digitado a aparecer branco sobre fundo claro/invisível.
     * O Metal LAF respeita corretamente as cores customizadas.
     */
    private static void aplicarTemaEscuroGlobal() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());

            UIManager.put("control", ChatWindow.Theme.BG_PRIMARY);
            UIManager.put("info", ChatWindow.Theme.BG_SURFACE);
            UIManager.put("nimbusBase", ChatWindow.Theme.BG_SURFACE);
            UIManager.put("nimbusBlueGrey", ChatWindow.Theme.BG_SURFACE);
            UIManager.put("Panel.background", ChatWindow.Theme.BG_PRIMARY);
            UIManager.put("OptionPane.background", ChatWindow.Theme.BG_SURFACE);
            UIManager.put("OptionPane.messageForeground", ChatWindow.Theme.TEXT_PRIMARY);
            UIManager.put("ToolTip.background", ChatWindow.Theme.BG_ELEVATED);
            UIManager.put("ToolTip.foreground", ChatWindow.Theme.TEXT_PRIMARY);
            UIManager.put("PopupMenu.background", ChatWindow.Theme.BG_ELEVATED);
            UIManager.put("MenuItem.background", ChatWindow.Theme.BG_ELEVATED);
            UIManager.put("MenuItem.foreground", ChatWindow.Theme.TEXT_PRIMARY);
            UIManager.put("Button.background", ChatWindow.Theme.BG_ELEVATED);
            UIManager.put("Button.foreground", ChatWindow.Theme.TEXT_PRIMARY);

            // ---- Reforço explícito para campos de texto e senha ----
            // (defesa extra além do setForeground/setBackground feito em
            // cada campo individualmente dentro de ChatWindow.java)
            UIManager.put("TextField.background", ChatWindow.Theme.BG_INPUT);
            UIManager.put("TextField.foreground", ChatWindow.Theme.TEXT_PRIMARY);
            UIManager.put("TextField.caretForeground", ChatWindow.Theme.TEXT_PRIMARY);
            UIManager.put("TextField.selectionBackground", ChatWindow.Theme.ACCENT_SOFT);
            UIManager.put("TextField.selectionForeground", ChatWindow.Theme.TEXT_PRIMARY);
            UIManager.put("TextField.inactiveForeground", ChatWindow.Theme.TEXT_MUTED);

            UIManager.put("PasswordField.background", ChatWindow.Theme.BG_INPUT);
            UIManager.put("PasswordField.foreground", ChatWindow.Theme.TEXT_PRIMARY);
            UIManager.put("PasswordField.caretForeground", ChatWindow.Theme.TEXT_PRIMARY);
            UIManager.put("PasswordField.selectionBackground", ChatWindow.Theme.ACCENT_SOFT);
            UIManager.put("PasswordField.selectionForeground", ChatWindow.Theme.TEXT_PRIMARY);

            UIManager.put("TextPane.background", ChatWindow.Theme.BG_SURFACE);
            UIManager.put("TextPane.foreground", ChatWindow.Theme.TEXT_PRIMARY);
            UIManager.put("TextPane.caretForeground", ChatWindow.Theme.TEXT_PRIMARY);

            UIManager.put("List.background", ChatWindow.Theme.BG_SURFACE);
            UIManager.put("List.foreground", ChatWindow.Theme.TEXT_PRIMARY);
            UIManager.put("List.selectionBackground", ChatWindow.Theme.ACCENT_SOFT);
            UIManager.put("List.selectionForeground", ChatWindow.Theme.TEXT_PRIMARY);

            UIManager.put("ScrollBar.background", ChatWindow.Theme.BG_SURFACE);
            UIManager.put("ScrollBar.thumb", ChatWindow.Theme.BORDER_SUBTLE);
            UIManager.put("ScrollBar.track", ChatWindow.Theme.BG_SURFACE);

        } catch (Exception e) {
            System.err.println("Não foi possível aplicar o tema escuro global: " + e.getMessage());
        }
    }
}
