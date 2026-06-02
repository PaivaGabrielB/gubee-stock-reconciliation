# Estágio 1: Build (Usando uma imagem que já vem com o Maven e JDK prontos)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# COPY do pom.xml primeiro para aproveitar o cache das dependências do Maven
COPY pom.xml .
# Baixa as dependências antes de copiar o código fonte. 
# Se o pom.xml não mudar, o Docker pula essa etapa nas próximas vezes!
RUN mvn dependency:go-offline -B

# Agora sim copiamos o código fonte e geramos o jar
COPY src ./src
RUN mvn clean package -DskipTests -q

# Estágio 2: Execução (Imagem final super leve, apenas com o JRE)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Cria um usuário do sistema para não rodar a aplicação como root (Boa prática de segurança!)
RUN addgroup -S gubee && adduser -S gubee -G gubee
USER gubee

# Copia o jar gerado no estágio anterior
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]