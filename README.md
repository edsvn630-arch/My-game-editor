# Ain Tradutor

Editor de falas dos jogos da AliceSoft (.ain, System 4) para Android.

- Abrir .ain, buscar, editar fala por fala, salvar .ain novo
- Importar/exportar CSV (colunas: ID, Original, Tradução; o ID é o número da fala, começando em 0)
- Opção para remover acentos e "ç" ao salvar (o jogo não mostra; sem a opção, viram "?")
- Devolve sozinho aspas, crase e espaço do início/fim da fala se faltarem
- Problemas e reajuste: lista falas que passam do limite, sem marcador, com caracteres que viram "?" ou incompletas; "Reajustar falas largas" redistribui as palavras nas mesmas linhas do trecho (com desfazer)
- Repetidas e substituir: preenche trechos idênticos ainda sem tradução e faz localizar/substituir nas traduções (com desfazer)
- OCR do modo ao vivo com PaddleOCR (PP-OCRv5 mobile, alfabeto latino) via ONNX Runtime; os modelos (~12 MB) são baixados uma vez e depois funciona offline
- Um projeto por jogo (as traduções de cada .ain ficam separadas e são retomadas ao reabrir o mesmo arquivo); lê .ain comprimido (AI2) e sem compressão
- Marcar falas para revisar (⚑) e tela de progresso por trecho do jogo
- Visual novo: tema claro/escuro automático, cartões coloridos por situação (verde traduzida, laranja rascunho, vermelho passou do limite), barra de progresso, tela inicial e botão flutuante "Ao vivo"
- Menu de três pontinhos com todas as opções
- Salvar direto na pasta do jogo (escolhe a pasta uma vez; guarda cópia .bak do arquivo original)
- Tradução automática inglês → português pelo Google Tradutor (online), como rascunho para revisar
- Barra de rolagem rápida na lateral direita da lista (arraste para ir longe, mostra o número da fala)
- "Limite": avisa em vermelho quando a fala passa de N caracteres
- Modo ao vivo: botão flutuante "Aa" sobre o jogo; lê a tela (OCR), acha a fala no .ain e abre a tela de edição na cena certa

O APK é gerado pelo GitHub Actions (aba Actions > Build APK > Artifacts).

## Créditos

- PaddleOCR (modelos PP-OCRv5, Apache-2.0): https://github.com/PaddlePaddle/PaddleOCR
- ONNX Runtime (MIT): https://onnxruntime.ai
