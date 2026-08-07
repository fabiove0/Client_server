# NEXUS.gov — Cliente de Comunicação Corporativa

Interface gráfica (Java Swing) de um cliente de chat corporativo,
com tema escuro, cantos arredondados e identidade visual própria.

**Este cliente já está conectado ao servidor real** (protocolo de
sockets TCP definido em `br.ufmt.chatcorporativo.protocol.ProtocolConstants`
do projeto do servidor). `Client.java` implementa login, envio/recebimento
de mensagens diretas e de grupo, listas de usuários/grupos, histórico e
transferência de arquivos, tudo via socket.

## Como testar

1. Rode o servidor primeiro (projeto separado):
   ```bash
   cd servidor-chatcorporativo-.../
   mvn compile
   mvn exec:java -Dexec.mainClass="br.ufmt.chatcorporativo.ServerMain"
   # ou: java -cp target/classes br.ufmt.chatcorporativo.ServerMain 5000 LOCAL
   ```
2. Compile e rode este cliente (veja abaixo). Por padrão ele conecta em
   `localhost:5000` — ajuste em `ChatWindow.aoClicarConectar()` se o
   servidor estiver em outra máquina/porta.
3. No login, qualquer usuário/senha/órgão funciona na primeira vez
   (o servidor registra automaticamente o usuário); em conexões
   seguintes a mesma senha precisa ser repetida.
4. Abra duas instâncias do cliente com usuários diferentes para trocar
   mensagens entre si.

## Estrutura

```
src/
├── Main.java          → ponto de entrada, aplica o tema escuro global
├── ChatWindow.java     → toda a interface gráfica (Swing)
├── Client.java         → contrato de comunicação (métodos vazios/stub)
├── FileManager.java    → seleção local de arquivos + delegação ao Client
├── assets/             → reservado para ícones/logotipos
└── styles/style.css    → paleta de cores documentada (referência visual)
```

## Como compilar e executar

Requer JDK 11 ou superior (testado com JDK 17).

```bash
cd src
javac -encoding UTF-8 -d ../bin Main.java ChatWindow.java Client.java FileManager.java
java -cp ../bin Main
```

> **Importante:** use sempre `-encoding UTF-8` ao compilar, pois o
> código contém comentários e textos em português com acentuação.

## Próximos passos (fora do escopo deste entregável)

1. Implementar o servidor (aceitação de múltiplas conexões, broadcast
   de mensagens, lista de usuários online).
2. Preencher os métodos de `Client.java`:
   `conectarServidor`, `enviarMensagem`, `receberMensagem`,
   `enviarArquivo`, `desconectar`.
3. Definir o protocolo de mensagens (texto delimitado, JSON, etc.).
4. Implementar a transferência de arquivos em blocos (chunking).
5. Adicionar autenticação/identidade digital, criptografia em trânsito
   (TLS) e demais requisitos de segurança e soberania de dados.
