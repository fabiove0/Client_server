import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * FileManager.java
 * -----------------------------------------------------------------------
 * Responsável pela parte LOCAL do envio de arquivos:
 *   - Abrir o seletor de arquivos (JFileChooser);
 *   - Validar o arquivo escolhido;
 *   - Confirmar com o usuário antes do envio;
 *   - Delegar o envio efetivo (rede) para o Client.
 *
 * Nenhuma transferência de bytes pela rede é feita aqui — isso é papel
 * do Client (ver Client.enviarArquivo), que hoje é apenas um placeholder.
 * -----------------------------------------------------------------------
 */
public class FileManager {

    private static final long TAMANHO_MAXIMO_SUGERIDO_MB = 50;

    /**
     * Abre o seletor de arquivos do sistema operacional e retorna o
     * arquivo escolhido pelo usuário, ou null se ele cancelar.
     */
    public File selecionarArquivo(Component parent) {
        JFileChooser seletor = new JFileChooser();
        seletor.setDialogTitle("Selecionar arquivo para envio");
        seletor.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int resultado = seletor.showOpenDialog(parent);

        if (resultado == JFileChooser.APPROVE_OPTION) {
            File arquivo = seletor.getSelectedFile();

            long tamanhoMB = arquivo.length() / (1024 * 1024);
            if (tamanhoMB > TAMANHO_MAXIMO_SUGERIDO_MB) {
                int confirmar = JOptionPane.showConfirmDialog(parent,
                        "O arquivo selecionado tem aproximadamente " + tamanhoMB
                                + " MB, acima do limite recomendado ("
                                + TAMANHO_MAXIMO_SUGERIDO_MB + " MB).\nDeseja continuar mesmo assim?",
                        "Arquivo grande",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (confirmar != JOptionPane.YES_OPTION) {
                    return null;
                }
            }
            return arquivo;
        }

        return null; // usuário cancelou a seleção
    }

    /**
     * Pede confirmação explícita ao usuário e, se confirmado, delega o
     * envio para o Client (ainda não implementado — ver TODOs em Client.java).
     *
     * @param parent       componente pai (para centralizar o diálogo)
     * @param arquivo      arquivo já selecionado (não nulo)
     * @param client       instância do cliente de comunicação
     * @param destinatario usuário que receberá o arquivo
     * @return true se o usuário confirmou e o envio foi disparado; false caso contrário
     */
    public boolean confirmarEEnviarArquivo(Component parent, File arquivo, Client client, String destinatario) {
        if (arquivo == null || !arquivo.exists() || arquivo.isDirectory()) {
            JOptionPane.showMessageDialog(parent,
                    "Arquivo inválido para envio.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        long tamanhoKB = Math.max(1, arquivo.length() / 1024);
        String tamanhoTexto = tamanhoKB >= 1024
                ? String.format("%.1f MB", tamanhoKB / 1024.0)
                : tamanhoKB + " KB";

        int confirmar = JOptionPane.showConfirmDialog(parent,
                "Enviar o arquivo \"" + arquivo.getName() + "\" (" + tamanhoTexto
                        + ") para " + destinatario + "?",
                "Confirmar envio de arquivo",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirmar != JOptionPane.YES_OPTION) {
            return false;
        }

        // Delega o envio real (rede) para o Client — ainda não implementado.
        client.enviarArquivo(arquivo, destinatario);
        return true;
    }
}
