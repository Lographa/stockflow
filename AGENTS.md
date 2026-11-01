# StockFlow — Regras do Projeto

## 1. Contexto

O StockFlow é uma aplicação de gerenciamento de produtos e estoque.

Tecnologias utilizadas:

- Backend: Java 21 e Spring Boot.
- Persistência: Spring Data JPA e MySQL.
- Cache: Redis.
- Frontend: Next.js e TypeScript.

## 2. Arquitetura

- Manter a organização atual do projeto.
- Separar responsabilidades entre Controller, Service e Repository.
- Controllers devem lidar com requisições HTTP.
- Services devem concentrar as regras de negócio.
- Repositories devem cuidar do acesso aos dados.
- Utilizar DTOs para entrada e saída de dados da API.
- Evitar duplicação de regras de negócio.

## 3. Regras de negócio

### Produtos

- O SKU deve ser único.
- O preço de um produto não pode ser negativo.
- Produtos inexistentes devem gerar um erro apropriado.
- Uma consulta sem produtos cadastrados deve retornar uma lista vazia.

### Movimentações de estoque

- Toda entrada ou saída deve gerar um registro de movimentação.
- A quantidade de uma movimentação deve ser maior que zero.
- Uma saída não pode deixar o estoque negativo.
- A atualização do estoque e o registro da movimentação devem ocorrer na mesma transação.
- O MySQL é a fonte oficial dos dados de estoque.
- O Redis não deve ser utilizado como fonte de verdade para decisões sobre a quantidade disponível.

## 4. Qualidade e segurança

- Não modificar funcionalidades existentes sem necessidade.
- Não adicionar dependências sem justificar sua necessidade.
- Criar testes automatizados para novas regras de negócio.
- Tratar erros de forma consistente com o padrão atual do projeto.
- Não alterar o esquema do banco de dados sem explicar a necessidade.
- Antes de implementar uma funcionalidade, analisar o código existente e apresentar um plano de alteração.
