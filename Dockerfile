# Caminho: Dockerfile
# Build em dois estágios: o primeiro compila com o JDK completo, o segundo
# carrega só o .jar sobre um JRE enxuto (imagem final menor e com menos superfície de ataque).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# Segurança: não rodar como root dentro do container.
RUN useradd --system --uid 1001 ledger
USER ledger
COPY --from=build /app/target/ledger-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
