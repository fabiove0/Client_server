# assets/

Esta pasta é reservada para os recursos visuais estáticos da aplicação:

- Ícone da aplicação (ex.: `app-icon.png`, referenciado em `Main.java`
  na chamada comentada `setIconImage(...)`);
- Ícones de botões em maior resolução (enviar, anexar, conectar), caso
  se deseje substituir os glifos de texto/emoji usados atualmente nos
  botões (`➤`, `📎`) por ícones vetoriais (SVG/PNG) próprios da
  identidade visual do sistema;
- Eventuais logotipos de órgãos/instituições que integrem a plataforma.

Nenhum arquivo binário foi incluído neste momento — apenas a interface
foi implementada. Basta adicionar os arquivos aqui e referenciá-los em
`ChatWindow.java` (por exemplo, com `new ImageIcon("src/assets/nome.png")`).
