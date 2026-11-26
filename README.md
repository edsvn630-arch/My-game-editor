# Ain Tradutor

Editor de falas dos jogos da AliceSoft (.ain, System 4) para Android.

- Abrir .ain, buscar, editar fala por fala, salvar .ain novo
- Importar/exportar CSV (colunas: ID, Original, Tradução; o ID é o número da fala, começando em 0)
- Opção para remover acentos e "ç" ao salvar (o jogo não mostra; sem a opção, viram "?")
- Devolve sozinho aspas, crase e espaço do início/fim da fala se faltarem
- Problemas e reajuste: lista falas que passam do limite, sem marcador, com caracteres que viram "?" ou incompletas; "Reajustar falas largas" redistribui as palavras nas mesmas linhas do trecho (com desfazer)
- Repetidas e substituir: preenche trechos idênticos ainda sem tradução e faz localizar/substituir nas traduções (com desfazer)
- Menu de três pontinhos com todas as opções
- Salvar direto na pasta do jogo (escolhe a pasta uma vez; guarda cópia .bak do arquivo original)
- Tradução automática inglês → português pelo Google Tradutor (online), como rascunho para revisar
- Barra de rolagem rápida na lateral direita da lista (arraste para ir longe, mostra o número da fala)
- "Limite": avisa em vermelho quando a fala passa de N caracteres
- Modo ao vivo: botão flutuante "Aa" sobre o jogo; lê a tela (OCR), acha a fala no .ain e abre a tela de edição na cena certa

O APK é gerado pelo GitHub Actions (aba Actions > Build APK > Artifacts).
