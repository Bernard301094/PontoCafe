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
- Banco atual: Neon PostgreSQL
- Persistência: PostgreSQL padrão, sem dependência de APIs proprietárias do provedor
- Backend/API: responsável por autenticação, regras, auditoria e acesso ao banco
- Idioma da interface: pt-BR

## Portabilidade
Neon é apenas o provedor PostgreSQL atual. O Android não acessa a base diretamente e não contém credenciais do banco. A API utiliza uma `DATABASE_URL`, permitindo migrar futuramente para outro PostgreSQL sem reescrever o aplicativo.

Veja `docs/ARQUITETURA_PORTAVEL.md`.

## Segurança
- O app não contém credenciais PostgreSQL.
- A hora oficial e as regras de pausa são validadas no servidor/banco.
- Senhas e códigos temporários são persistidos apenas como hashes.
- Dados biométricos são armazenados como templates/embeddings cifrados, não como fotos brutas.
- PDFs são gerados sob demanda e não ocupam espaço no banco por padrão.
- Acesso administrativo e alterações sensíveis são auditáveis.

## Status
Estrutura inicial do MVP em desenvolvimento.
