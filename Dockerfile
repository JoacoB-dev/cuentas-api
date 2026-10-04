# Etapa 1: compilar con Maven. Los tests no corren acá porque necesitan PostgreSQL;
# corren en CI (ver .github/workflows/ci.yml) contra un contenedor de Postgres.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Etapa 2: imagen liviana sólo con el JRE y el jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/cuentas-api-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
