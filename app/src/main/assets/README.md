# Modelo facial do Ponto Café

O APK espera um modelo LiteRT/TFLite chamado `facenet.tflite` neste diretório.

Contrato atual do motor (`LiteRtFaceEmbeddingEngine`):

- entrada: imagem RGB `160 x 160`;
- tipo: `FLOAT32`;
- pré-processamento: padronização por imagem `(pixel - média) / desvio-padrão`;
- saída: embedding `Float32` de 128 dimensões;
- pós-processamento: normalização L2;
- o mesmo modelo e a mesma versão devem ser usados no cadastro e na identificação.

O arquivo binário do modelo não deve ser adicionado ao projeto de produção sem verificar a origem, a licença dos pesos pré-treinados e a adequação para uso empresarial. A implementação Android já está conectada ao LiteRT e fica operacional assim que um modelo compatível e aprovado for empacotado como `facenet.tflite`.

Antes de liberar em produção, calibre `FACE_MATCH_THRESHOLD` e `FACE_IDENTIFICATION_MARGIN` com amostras reais do ambiente de uso e valide falsos positivos/falsos negativos.
