# Ponto Café

Aplicativo Android para controle de pausas de café com validação facial, autorização de supervisor e relatórios.

## Regras iniciais
- Manhã: 08:00–10:00
- Tarde: 15:00–17:00
- Limite por período: 7 min 30 s
- O saldo não acumula entre períodos
- Fora do horário exige autorização temporária de supervisor

## Arquitetura
- Android: Kotlin + Jetpack Compose
- Câmera: CameraX
- Detecção facial: ML Kit
- Banco: Neon PostgreSQL
- Autenticação: Neon Auth
- Idioma da interface: pt-BR

## Segurança
- O app não deve conter credenciais PostgreSQL.
- A hora oficial e as regras de pausa devem ser validadas no backend/banco.
- Dados biométricos devem ser armazenados como templates/embeddings criptografados, não como fotos brutas.
- Acesso administrativo deve ser auditável.

## Status
Estrutura inicial do MVP em desenvolvimento.
