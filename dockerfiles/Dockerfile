# VozViva API - imagem para o Azure App Service (container Linux).
# Build a partir da raiz do repositório:
#   docker build -f dockerfiles/Dockerfile -t vozviva-api .
# Os testes rodam na pipeline antes do build da imagem (azure-pipeline.yml), por isso aqui só o jar é gerado.

# ---- Etapa 1: compilação com o Gradle Wrapper (JDK 21) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dependências primeiro: mudar só o código não baixa tudo de novo.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null

COPY src ./src
RUN ./gradlew bootJar --no-daemon -q \
    && java -Djarmode=tools -jar build/libs/vozviva.jar extract --layers --launcher --destination build/extraido

# ---- Etapa 2: execução só com o JRE 21 ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuário sem privilégios: a API nunca roda como root.
RUN addgroup -S vozviva && adduser -S vozviva -G vozviva

# Camadas do Spring Boot, da que menos muda para a que mais muda (melhor cache no ACR).
COPY --from=build --chown=vozviva:vozviva /app/build/extraido/dependencies/ ./
COPY --from=build --chown=vozviva:vozviva /app/build/extraido/spring-boot-loader/ ./
COPY --from=build --chown=vozviva:vozviva /app/build/extraido/snapshot-dependencies/ ./
COPY --from=build --chown=vozviva:vozviva /app/build/extraido/application/ ./

USER vozviva

ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# Liveness: o processo está de pé (não depende do broker nem do banco).
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/actuator/health/liveness" > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
