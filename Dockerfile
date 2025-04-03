# Stage 1: Build a aplicação usando Maven e JDK
FROM eclipse-temurin:21-jdk-jammy AS builder
# Ou use a imagem Maven correspondente à sua versão Java/Maven

# Define o diretório de trabalho dentro do container
WORKDIR /app

# Copia os descritores do Maven e baixa dependências (camada cacheada)
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Baixa as dependências
RUN ./mvnw dependency:go-offline -B

# Copia o restante do código fonte
COPY src ./src

# Compila a aplicação e empacota o JAR, pulando os testes
# O JAR será criado em /app/target/order-service-0.0.1-SNAPSHOT.jar (ou nome similar)
RUN ./mvnw package -DskipTests

# Stage 2: Cria a imagem final usando apenas o JRE e o JAR compilado
FROM eclipse-temurin:21-jre-jammy
# Ou outra imagem JRE base leve

WORKDIR /app

# Copia o JAR da fase de build para a imagem final
COPY --from=builder /app/target/*.jar app.jar

# Expõe a porta que a aplicação Spring Boot usa (padrão 8080)
EXPOSE 8080

# Define o comando para executar a aplicação quando o container iniciar
ENTRYPOINT ["java", "-jar", "app.jar"]

# Opcional: Adicionar argumentos JVM como -Xmx, -Xms aqui ou via docker-compose
# ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]