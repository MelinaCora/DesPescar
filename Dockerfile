# Imagen generica para cualquier microservicio: se elige con --build-arg SERVICE=<carpeta en services/>.
# El contexto de build es la raiz del repo (hace falta para compilar common-security junto al servicio).
FROM maven:3.9-eclipse-temurin-17 AS build
ARG SERVICE
WORKDIR /src
COPY pom.xml .
COPY services ./services
RUN mvn -q -B -pl services/${SERVICE} -am -DskipTests package \
    && cp services/${SERVICE}/target/*-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:17-jre
RUN useradd --system --no-create-home despescar
COPY --from=build /app.jar /app.jar
USER despescar
# JAVA_OPTS se define en docker-compose.prod.yml para limitar la memoria de cada servicio.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app.jar"]
