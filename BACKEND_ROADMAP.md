# StockFlow — Backend Roadmap

## Objetivo

Construir um backend de gerenciamento de estoque com Java, Spring Boot, MySQL, Redis, Spring Security, JWT e Docker, praticando arquitetura, segurança, processamento assíncrono e escalabilidade horizontal.

## Stack planejada

- [ ] Java 21
- [ ] Spring Boot
- [ ] Maven
- [ ] Spring Web
- [ ] Spring Data JPA / Hibernate
- [ ] MySQL
- [ ] Bean Validation
- [ ] Lombok
- [ ] Spring Security
- [ ] JWT
- [ ] Spring Data Redis
- [ ] Redis Streams para processamento assíncrono
- [ ] JUnit 5 e Mockito
- [ ] Docker e Docker Compose

---

## Fase 1 — CRUD de produtos

### Modelagem e estrutura

- [x] Configurar a conexão com MySQL por variáveis de ambiente.
- [ ] Conferir a estrutura de pacotes por funcionalidade.
- [x] Criar a entidade `Product`.
- [ ] Configurar os campos e as colunas da entidade.
- [x] Configurar `createdAt` e `updatedAt`.
- [x] Criar `ProductRepository`.
- [x] Criar DTOs de requisição e resposta.
- [x] Criar `ProductMapper`.

### Regras de negócio

- [ ] Criar `ProductService`.
- [ ] Implementar cadastro de produtos.
- [ ] Impedir cadastro de produtos com SKU duplicado.
- [ ] Implementar busca de produto por ID.
- [ ] Implementar listagem de produtos.
- [ ] Implementar atualização de produtos.
- [ ] Implementar exclusão de produtos.
- [ ] Criar `ProductController`.
- [ ] Validar campos obrigatórios, preço e quantidade.
- [ ] Retornar códigos HTTP adequados.
- [ ] Criar tratamento global de exceções.

### Testes e conclusão

- [ ] Criar testes unitários do serviço.
- [ ] Criar testes para validações e erros.
- [ ] Testar os endpoints com Postman, Insomnia ou similar.
- [ ] Confirmar que a API não expõe diretamente a entidade JPA.
- [ ] Documentar os endpoints iniciais.

**Critério para concluir:** CRUD funcional, validações funcionando e testes passando.

---

## Fase 2 — Movimentações de estoque

### Modelagem

- [ ] Criar a entidade `StockMovement`.
- [ ] Relacionar a movimentação ao produto.
- [ ] Definir os tipos `ENTRY` e `EXIT`.
- [ ] Criar repository, DTOs e mapper de movimentações.

### Regras de negócio

- [ ] Implementar entrada de estoque.
- [ ] Implementar saída de estoque.
- [ ] Impedir saída maior que o estoque disponível.
- [ ] Atualizar a quantidade do produto ao registrar movimentações.
- [ ] Usar `@Transactional` para manter as operações consistentes.
- [ ] Criar consulta do histórico de movimentações por produto.
- [ ] Impedir alterações inconsistentes no estoque.
- [ ] Avaliar concorrência em movimentações simultâneas.

### Testes e conclusão

- [ ] Testar entrada e saída.
- [ ] Testar estoque insuficiente.
- [ ] Testar rollback em caso de erro.
- [ ] Testar movimentações concorrentes.

**Critério para concluir:** o estoque só muda quando a movimentação é válida e a operação é concluída corretamente.

---

## Fase 3 — Login e segurança

### Usuários e autenticação

- [ ] Criar a entidade `User`.
- [ ] Criar repository, DTOs, service e controller necessários.
- [ ] Configurar Spring Security.
- [ ] Implementar cadastro de usuários.
- [ ] Armazenar senhas com BCrypt.
- [ ] Implementar login.
- [ ] Gerar e validar JWT.
- [ ] Configurar o filtro de autenticação JWT.
- [ ] Definir endpoints públicos e protegidos.
- [ ] Retornar `401 Unauthorized` quando não houver autenticação válida.

### Autorização

- [ ] Criar roles como `USER` e `ADMIN`.
- [ ] Restringir operações administrativas.
- [ ] Aplicar autorização nos endpoints.
- [ ] Garantir que usuários não acessem recursos de outros usuários indevidamente.
- [ ] Não confiar em roles ou IDs enviados livremente pelo cliente.
- [ ] Configurar expiração do token.
- [ ] Avaliar estratégia de refresh token, se necessário.

### Testes e conclusão

- [ ] Testar login correto e incorreto.
- [ ] Testar token ausente, inválido e expirado.
- [ ] Testar permissões de cada role.
- [ ] Testar acesso não autorizado a recursos.

**Critério para concluir:** endpoints protegidos e regras de autorização verificadas por testes.

---

## Fase 4 — API de geração de etiquetas assíncrona

Inicialmente, simular a geração de uma etiqueta para não depender de uma transportadora externa.

### API e processamento

- [ ] Criar endpoint para solicitar uma etiqueta.
- [ ] Validar os dados da solicitação.
- [ ] Criar um identificador único para cada trabalho.
- [ ] Retornar `202 Accepted` com o identificador do trabalho.
- [ ] Definir estados como `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`.
- [ ] Criar um serviço de processamento em background.
- [ ] Enfileirar trabalhos no Redis Streams.
- [ ] Criar um consumidor para processar os trabalhos.
- [ ] Persistir o estado do trabalho no MySQL.
- [ ] Criar endpoint para consultar o status.
- [ ] Disponibilizar o resultado da etiqueta quando concluída.
- [ ] Simular falhas no processamento.
- [ ] Implementar política de retry e tratamento de mensagens com falha.

### Confiabilidade

- [ ] Evitar processar o mesmo trabalho duas vezes de forma indevida.
- [ ] Definir estratégia para mensagens que falham repetidamente.
- [ ] Garantir que o status persistido seja consistente com o processamento.
- [ ] Avaliar o comportamento se a aplicação reiniciar durante um trabalho.

### Testes e conclusão

- [ ] Testar solicitação e consulta de status.
- [ ] Testar processamento bem-sucedido.
- [ ] Testar falhas e tentativas de reprocessamento.
- [ ] Testar solicitações duplicadas.

**Critério para concluir:** a API aceita uma solicitação rapidamente e o trabalho é processado separadamente, com status consultável.

---

## Fase 5 — Redis e estado compartilhado

### Integração

- [ ] Configurar Redis por variáveis de ambiente.
- [ ] Centralizar a configuração de conexão.
- [ ] Definir separadamente o uso de cache e de fila.
- [ ] Usar Redis Streams para comunicação assíncrona.
- [ ] Configurar grupos de consumidores quando apropriado.

### Cache

- [ ] Identificar consultas que se beneficiam de cache.
- [ ] Implementar cache para uma consulta de leitura, se fizer sentido.
- [ ] Definir TTL.
- [ ] Invalidar ou atualizar o cache quando os dados mudarem.
- [ ] Evitar retornar dados desatualizados indevidamente.

### Testes e conclusão

- [ ] Testar conexão e indisponibilidade do Redis.
- [ ] Testar expiração e invalidação do cache.
- [ ] Testar consumo de mensagens por mais de um consumidor.
- [ ] Documentar como o sistema se recupera de falhas.

**Critério para concluir:** cache e processamento assíncrono funcionam sem depender de estado armazenado apenas na memória de uma instância.

---

## Fase 6 — Docker e ambiente reproduzível

### Containerização

- [ ] Criar `Dockerfile` para o backend.
- [ ] Usar build em múltiplos estágios quando apropriado.
- [ ] Executar a aplicação com usuário não privilegiado.
- [ ] Criar `docker-compose.yml`.
- [ ] Configurar serviços do backend, MySQL e Redis.
- [ ] Configurar rede e persistência do MySQL.
- [ ] Configurar persistência do Redis quando necessária.
- [ ] Configurar variáveis de ambiente.
- [ ] Adicionar health checks.
- [ ] Garantir que a aplicação aguarde as dependências ficarem disponíveis.
- [ ] Não incluir senhas ou segredos na imagem.
- [ ] Documentar como subir e parar o ambiente.

### Testes e conclusão

- [ ] Construir a imagem do backend.
- [ ] Subir o ambiente do zero com Docker Compose.
- [ ] Testar os endpoints no ambiente containerizado.
- [ ] Confirmar que os dados persistem após reiniciar os containers.
- [ ] Testar reinício de serviços.

**Critério para concluir:** outra pessoa consegue executar o backend e suas dependências seguindo as instruções do projeto.

---

## Fase 7 — Escalabilidade horizontal

### Múltiplas instâncias

- [ ] Garantir que o backend não dependa de estado local para autenticação.
- [ ] Garantir que sessões, quando existentes, sejam compartilhadas ou evitadas.
- [ ] Configurar múltiplas instâncias da API.
- [ ] Adicionar um balanceador de carga, como Nginx.
- [ ] Distribuir requisições entre as instâncias.
- [ ] Manter banco e Redis acessíveis pelas instâncias.
- [ ] Avaliar limites de conexões com o MySQL.
- [ ] Configurar limites de concorrência do processamento assíncrono.
- [ ] Verificar que consumidores do Redis Streams não processam indevidamente a mesma mensagem.
- [ ] Definir estratégia para mensagens pendentes após falha de um consumidor.
- [ ] Validar idempotência das operações críticas.

### Testes de escala

- [ ] Executar duas ou mais instâncias do backend.
- [ ] Testar autenticação através do balanceador.
- [ ] Testar movimentações simultâneas de estoque.
- [ ] Testar processamento assíncrono com múltiplos consumidores.
- [ ] Executar um teste de carga básico.
- [ ] Observar latência, erros e uso de recursos.
- [ ] Documentar gargalos e melhorias possíveis.

**Critério para concluir:** a aplicação funciona com múltiplas instâncias sem depender da memória local para compartilhar estado.

---

## Fase 8 — Qualidade, observabilidade e documentação

- [ ] Revisar a cobertura dos testes.
- [ ] Adicionar testes de integração para os fluxos principais.
- [ ] Padronizar respostas de erro.
- [ ] Configurar logs úteis sem expor senhas ou tokens.
- [ ] Configurar Spring Boot Actuator e health checks.
- [ ] Revisar validação de entrada e autorização.
- [ ] Revisar configurações por ambiente.
- [ ] Documentar endpoints e exemplos de requisição.
- [ ] Documentar arquitetura e decisões técnicas.
- [ ] Atualizar README com instalação, execução e testes.
- [ ] Documentar as limitações conhecidas do projeto.

**Critério para concluir:** projeto reproduzível, documentado e com os principais fluxos cobertos por testes.

---

## Regras de trabalho com o Cursor

1. Trabalhar em apenas uma fase por vez.
2. Não implementar automaticamente as fases seguintes.
3. Antes de alterar arquivos, analisar a estrutura existente.
4. Explicar brevemente o objetivo de cada alteração.
5. Preferir mudanças pequenas e incrementais.
6. Não substituir código existente sem explicar o motivo.
7. Não adicionar dependências sem justificar sua necessidade.
8. Criar ou atualizar testes junto com as funcionalidades.
9. Executar os testes pertinentes ao terminar cada tarefa.
10. Marcar uma tarefa como concluída somente depois de verificar seu funcionamento.
11. Se houver uma decisão arquitetural importante, explicar alternativas e recomendar uma.
12. Ao terminar uma tarefa, informar quais arquivos foram alterados, como testar e qual é a próxima tarefa.
13. Manter este arquivo atualizado, sem marcar tarefas que ainda não foram verificadas.
14. Priorizar explicações que ajudem a entender Java e Spring Boot, em vez de apenas gerar código.

## Estado atual

Começar pela Fase 1 e identificar quais tarefas já foram realizadas. Não recriar arquivos ou estruturas que já existam. Aguardar a validação da tarefa atual antes de avançar para a próxima.
