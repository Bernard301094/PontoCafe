# Ponto Café — privacidade e governança biométrica

Este documento registra os controles técnicos esperados para a versão 1.0 do Ponto Café. Ele complementa, mas não substitui, a análise jurídica e as políticas internas aplicáveis ao uso de biometria no ambiente de trabalho.

## Princípios técnicos

1. **Finalidade definida:** a biometria é utilizada para confirmar a identidade no fluxo de pausa do café e para administração/calibração autorizada.
2. **Minimização:** armazenar e transmitir somente o necessário para a finalidade técnica.
3. **Separação de finalidade:** avatar visual não é template biométrico e não participa do reconhecimento.
4. **Menor privilégio:** ações de gestão e diagnóstico exigem perfis autorizados no backend.
5. **Retenção controlada:** templates de colaboradores desativados obedecem à política configurada; o valor padrão atual é 90 dias, podendo ser alterado por configuração do backend.
6. **Auditabilidade:** alterações relevantes de biometria, usuários e dispositivos devem deixar trilha de auditoria sem expor o dado biométrico bruto.

## Dados biométricos

O fluxo facial trabalha com embeddings/templates derivados da captura de câmera. A imagem capturada para reconhecimento/calibração não deve ser usada como telemetria nem incluída em logs operacionais.

O cadastro, identificação e calibração devem manter as seguintes garantias:

- não escrever embedding bruto em logs;
- não incluir foto ou embedding na telemetria de saúde;
- não incluir embedding em mensagens de erro;
- não usar avatar como entrada do FaceNet;
- não reduzir thresholds silenciosamente para aumentar taxa de aceite;
- preservar modelo/normalização compatíveis com os templates cadastrados enquanto não existir migração biométrica deliberada.

## Journal de integridade 1.0

O `PontoOperationJournal` existe para recuperar uma mutação de Ponto com resultado de rede incerto. Ele pode persistir:

- UUID da operação;
- identificador do colaborador;
- fingerprint SHA-256 do embedding já processado em RAM ou etiqueta da ação;
- timestamp;
- estado de incerteza.

Ele **não deve persistir**:

- fotografia;
- embedding facial bruto;
- PIN;
- senha;
- token de sessão;
- token do dispositivo.

O journal é cifrado com AES-GCM e chave protegida pelo Android Keystore.

## Telemetria de saúde

A telemetria operacional do dispositivo é restrita a metadados técnicos como:

- versão do app;
- modelo do aparelho;
- versão Android;
- contadores/timestamps de crash;
- contadores/timestamps de travamento prolongado.

É proibido adicionar a esse payload:

- foto;
- embedding;
- template facial;
- PIN;
- senha;
- token de ativação;
- token de sessão;
- chave do banco;
- `BIOMETRIC_MASTER_KEY`.

## Diagnóstico biométrico

O diagnóstico pode apresentar informações derivadas necessárias para medir precisão, por exemplo:

- score do candidato correto;
- score do concorrente mais próximo;
- margem;
- threshold;
- Top-1 accuracy;
- FRR;
- FAR;
- quantidade de amostras/comparações.

Esses dados devem ser exibidos de forma agregada ou vinculada a uma calibração autorizada. A tela não precisa armazenar a imagem de calibração para produzir o resultado.

## Retenção e exclusão

A política técnica deve permitir:

- excluir a biometria de um colaborador por ação administrativa autorizada;
- revogar verificações temporárias relacionadas;
- executar limpeza automática de templates retidos após o prazo configurado;
- preservar histórico operacional quando necessário para auditoria sem conservar indefinidamente o template biométrico.

A exclusão de um colaborador não deve apagar silenciosamente registros históricos de Ponto necessários à rastreabilidade.

## Controle de acesso

- Administração de usuários, dispositivos, auditoria e políticas: `ADMIN`.
- Funções operacionais delegadas: `SUPERVISOR` conforme rotas autorizadas.
- O dispositivo de Ponto usa token próprio e não recebe credenciais administrativas.
- PIN de desbloqueio é validado de forma segura e não deve ser armazenado em texto puro.
- Desativar/rotacionar um dispositivo deve invalidar a credencial anterior conforme o fluxo previsto.

## Logs e observabilidade

Logs podem conter request ID, códigos de erro, tempos e identificadores técnicos necessários para investigação. Devem ser redigidos para evitar segredos e dados biométricos.

Em especial, nunca registrar intencionalmente:

```text
DATABASE_URL
BIOMETRIC_MASTER_KEY
CODE_PEPPER
senha
PIN
Authorization bearer
X-Device-Token
embedding completo
foto facial
```

## Incidente de privacidade/segurança

Ao suspeitar de exposição indevida:

1. preservar logs e request IDs sem reproduzir o segredo exposto;
2. revogar tokens/sessões afetados;
3. rotacionar segredo comprometido conforme necessidade;
4. limitar temporariamente a superfície afetada;
5. identificar quais dados e período foram envolvidos;
6. registrar correção e testes que impedem recorrência;
7. seguir o procedimento interno/jurídico aplicável para comunicação e resposta ao incidente.

## Gate da versão 1.0

Antes de declarar a Release estável, confirmar que:

- telemetria não contém biometria ou credenciais;
- diagnóstico não grava fotos;
- journal idempotente não guarda embedding bruto;
- exclusão/retensão biométrica funciona;
- acesso Admin/Supervisor é aplicado no servidor;
- dispositivo revogado perde acesso;
- logs de erro redigem credenciais;
- nenhum segredo está versionado no repositório.
