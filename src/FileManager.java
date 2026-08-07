import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * FileManager.java
 * -----------------------------------------------------------------------
 * Responsável pela parte LOCAL do envio, abertura e salvamento de arquivos:
 *   - Abrir o seletor de arquivos (JFileChooser);
 *   - Validar o arquivo escolhido;
 *   - Confirmar com o usuário antes do envio;
 *   - Delegar o envio efetivo (rede) para o Client;
 *   - Abrir arquivos com o aplicativo padrão do SO;
 *   - Exportar/salvar arquivos em locais escolhidos pelo usuário.
 * -----------------------------------------------------------------------
 */
public class FileManager {

    private static final long TAMANHO_MAXIMO_SUGERIDO_MB = 50;
    private static final File PASTA_DOWNLOADS = new File("downloads_nexus");

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
     * envio para o Client.
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

        // Delega o envio real (rede) para o Client
        client.enviarArquivo(arquivo, destinatario);
        return true;
    }

    /**
     * Localiza um arquivo no sistema de arquivos. Primeiro verifica se é um
     * caminho válido/existente, senão procura dentro da pasta 'downloads_nexus'.
     */
    public File resolverArquivo(String nomeOuCaminho) {
        if (nomeOuCaminho == null || nomeOuCaminho.trim().isEmpty()) {
            return null;
        }

        File f = new File(nomeOuCaminho);
        if (f.exists()) {
            return f;
        }

        File emDownloads = new File(PASTA_DOWNLOADS, f.getName());
        if (emDownloads.exists()) {
            return emDownloads;
        }

        return f; // retorna a referência mesmo que ainda não exista em disco
    }

    /**
     * Tenta abrir o arquivo usando o aplicativo padrão configurado no sistema operacional.
     */
    public void abrirArquivo(Component parent, File arquivo) {
        if (arquivo == null || !arquivo.exists()) {
            JOptionPane.showMessageDialog(parent,
                    "O arquivo não foi encontrado no computador.\nCaminho: " +
                            (arquivo != null ? arquivo.getAbsolutePath() : "desconhecido"),
                    "Arquivo não encontrado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            JOptionPane.showMessageDialog(parent,
                    "A abertura automática de arquivos não é suportada neste sistema operacional.\n" +
                            "O arquivo está salvo em:\n" + arquivo.getAbsolutePath(),
                    "Recurso não suportado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Desktop.getDesktop().open(arquivo);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "Erro ao tentar abrir o arquivo: " + e.getMessage() +
                            "\nCaminho: " + arquivo.getAbsolutePath(),
                    "Erro ao abrir arquivo", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Abre a pasta onde o arquivo está armazenado no gerenciador de arquivos do SO.
     */
    public void abrirPasta(Component parent, File arquivo) {
        File pasta = (arquivo != null && arquivo.getParentFile() != null && arquivo.getParentFile().exists())
                ? arquivo.getParentFile()
                : PASTA_DOWNLOADS;

        if (!pasta.exists()) {
            pasta.mkdirs();
        }

        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            JOptionPane.showMessageDialog(parent,
                    "Não foi possível abrir o gerenciador de arquivos.\n" +
                            "Caminho da pasta: " + pasta.getAbsolutePath(),
                    "Recurso não suportado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Desktop.getDesktop().open(pasta);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "Erro ao abrir a pasta: " + e.getMessage() +
                            "\nCaminho: " + pasta.getAbsolutePath(),
                    "Erro ao abrir pasta", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Abre uma caixa de diálogo "Salvar Como" para permitir que o usuário salve/copie
     * o arquivo para qualquer diretório do computador.
     */
    public void salvarComo(Component parent, File arquivoOrigem) {
        if (arquivoOrigem == null || !arquivoOrigem.exists()) {
            JOptionPane.showMessageDialog(parent,
                    "O arquivo de origem não foi encontrado para salvar.",
                    "Erro ao salvar", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser seletor = new JFileChooser();
        seletor.setDialogTitle("Salvar arquivo como...");
        seletor.setSelectedFile(new File(arquivoOrigem.getName()));

        int resultado = seletor.showSaveDialog(parent);
        if (resultado == JFileChooser.APPROVE_OPTION) {
            File destino = seletor.getSelectedFile();
            if (destino.exists()) {
                int sobrescrever = JOptionPane.showConfirmDialog(parent,
                        "O arquivo \"" + destino.getName() + "\" já existe na pasta de destino.\nDeseja sobrescrevê-lo?",
                        "Confirmar substituição",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (sobrescrever != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            try {
                Files.copy(arquivoOrigem.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
                JOptionPane.showMessageDialog(parent,
                        "Arquivo salvo com sucesso em:\n" + destino.getAbsolutePath(),
                        "Download concluído", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(parent,
                        "Erro ao salvar o arquivo: " + e.getMessage(),
                        "Erro ao salvar", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

