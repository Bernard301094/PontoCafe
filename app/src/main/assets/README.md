# Assets do APK

Aqui mora **apenas** o modelo de voz neural (`voice/`), que o
`PontoNeuralVoice.copyAssetTree` copia para o armazenamento do aparelho no
primeiro arranque.

## Nenhum modelo biométrico entra neste diretório

Este ficheiro descrevia, até agora, um `facenet.tflite` de 160×160 que o APK
supostamente esperava, com um motor `LiteRtFaceEmbeddingEngine` e dois limiares
de calibração. Nada disso existe desde a migração `012_access_codes.sql`: o
reconhecimento facial foi **removido**, as tabelas `templates_faciais` e
`verificacoes_faciais` foram apagadas com os dados biométricos dentro, e o
motor, os limiares e as rotas saíram junto.

O texto antigo sobreviveu por descuido na restauração do repositório, e custou
o que um documento errado costuma custar: alguém leu "o APK espera um modelo
chamado facenet.tflite neste diretório", cumpriu a instrução, e o APK passou a
carregar 23,7 MB de pesos que nenhuma linha de código consegue abrir — não há
runtime LiteRT/TFLite no projeto. O ficheiro foi removido.

Quem hoje autoriza uma pausa é o **código de acesso de 6 caracteres** emitido
por Supervisor ou Administrador, opcionalmente lido por câmara como QR. A
câmara que existe no APK lê um quadrado preto e branco e devolve texto, via
ZXing: não extrai template, não guarda imagem e não carrega modelo nenhum.

Se a identificação biométrica voltar a ser desejada algum dia, ela volta por uma
decisão explícita — com base legal, aviso às pessoas e um caminho de exclusão do
dado —, não por um binário deixado numa pasta. Até lá, o contrato de release
recusa qualquer `.tflite` aqui dentro e qualquer dependência de visão no Gradle.
