# Alexandria API

## Build
mvn -q -DskipTests package

## Docker
docker build -t <seu-registry>/alexandria-api:0.1.0 .
docker push <seu-registry>/alexandria-api:0.1.0

## Koyeb (variáveis obrigatórias)
- DB_URL        (jdbc:postgresql://.../postgres?sslmode=require)
- DB_USER       (p.ex.: alexapi)
- DB_PASSWORD
- JWT_SECRET    (base64 com alta entropia)

## Endpoints principais
- Auth: POST /api/auth/login, /api/auth/change-password, /api/auth/logout
- CRUD genérico: /api/{tabela}...
- Dashboards: /api/dash/...
- Vendas/PDV: /api/vendas/...
- Estoque: /api/estoque/...

## Swagger
/swagger-ui/index.html
