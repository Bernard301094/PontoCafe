import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = (p: string) => readFileSync(new URL(`../../${p}`, import.meta.url), 'utf8')

const migracao = read('database/013_colaborador_nome_unico.sql')
const gestao = read('backend/src/routes/collaborator-management-routes.ts')
const workforce = read('backend/src/routes/workforce-routes.ts')

test('dois colaboradores ativos não podem ter o mesmo nome', () => {
  // O quiosque lista nomes para a pessoa tocar no próprio. Com dois "João
  // Silva" idênticos ela não tem como saber qual é o dela, e metade das vezes
  // abre a pausa na linha errada -- e o relatório divide a jornada de uma
  // pessoa entre dois registos.
  assert.match(migracao, /create unique index if not exists ux_colaborador_nome_ativo/)
  assert.match(migracao, /where ativo = true/)

  // A unicidade é sobre o nome normalizado: "JOÃO SILVA", "João Silva" e
  // "Joao  Silva" são a mesma pessoa a tentar entrar três vezes.
  assert.match(migracao, /create or replace function pontocafe_nome_normalizado/)
  assert.match(migracao, /immutable/)
  assert.match(migracao, /translate\(/)
  assert.match(migracao, /regexp_replace\(/)

  // Só ativos: quem sai é arquivado com ativo=false e o histórico fica. Se essa
  // pessoa voltar, ou outra com o mesmo nome for contratada, tem de dar.
  assert.doesNotMatch(migracao, /create unique index[\s\S]{0,200}on colaboradores \(nome\)/)
})

test('as três portas de entrada respeitam a mesma regra', () => {
  // Criação e edição traduzem o 23505 em vez de devolver um 500 genérico: o
  // operador precisa de saber que o problema é o nome, senão tenta de novo.
  assert.match(gestao, /function nomeDuplicado\(erro: unknown\)/)
  assert.match(gestao, /ux_colaborador_nome_ativo/)
  assert.equal(
    (gestao.match(/if \(nomeDuplicado\(erro\)\) return c\.json\(\{ erro: ERRO_NOME_DUPLICADO \}, 409\)/g) ?? []).length,
    2,
    'criação e edição precisam ambas traduzir o erro',
  )

  // O CSV comparava nome + setor + turno os três: importar "João Silva /
  // Produção" quando já havia "João Silva" sem setor criava um segundo.
  assert.match(workforce, /pontocafe_nome_normalizado\(nome\)=pontocafe_nome_normalizado\(\$1\)/)
  assert.doesNotMatch(
    workforce,
    /and lower\(coalesce\(trim\(setor\),''\)\)=lower\(coalesce\(trim\(\$2\),''\)\)/,
    'a checagem do CSV não pode voltar a exigir setor e turno iguais',
  )
})
