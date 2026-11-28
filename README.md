# Alice Editor

Editor de falas dos jogos da AliceSoft (.ain, System 4) para Android. Versão 1.0.0 (primeiro release).

## Recursos

- Abrir o .ain, buscar, editar fala por fala e salvar o .ain novo (botões de abrir e salvar na barra do topo)
- Salvar direto na pasta do jogo (escolhe a pasta uma vez; guarda cópia .bak do arquivo original)
- Opções rápidas acima da busca: limite de caracteres, só não traduzidas, remover acentos e "ç", falas vazias
- Importar/exportar CSV (colunas: ID, Original, Tradução; o ID é o número da fala, começando em 0)
- Limite de caracteres geral e por fala; avisa quando a tradução passa do limite
- Problemas e reajuste: lista o que pode dar erro no jogo e redistribui as palavras nas mesmas linhas do trecho
- Falas repetidas, localizar e substituir, e tradução automática (Google Tradutor, online), cada uma na sua tela
- Marcar falas para revisar (⚑), rascunhos automáticos e tela de progresso por trecho do jogo
- Um projeto por jogo: as traduções de cada .ain ficam separadas
- Modo ao vivo: botão flutuante sobre o jogo; lê a tela com PaddleOCR (modelos de ~12 MB baixados uma vez, depois funciona offline), acha a fala no .ain e abre a edição na cena certa
- Tema claro/escuro automático

## Compilar

O APK é gerado pelo GitHub Actions (aba Actions > Build > Artifacts). O app é assinado com a chave fixa
`app/alice-release.jks`, então cada versão nova instala por cima da anterior sem perder dados.

## Créditos

- PaddleOCR (modelos PP-OCRv5, Apache-2.0): https://github.com/PaddlePaddle/PaddleOCR
- ONNX Runtime (MIT): https://onnxruntime.ai
