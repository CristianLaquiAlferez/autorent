# ---------------------------------------------------------------
# AutoRent - imagen para Railway (o cualquier hosting con Docker)
# Etapa 1: compila con Maven. Etapa 2: solo Java 17 + el .jar.
# ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS compilacion
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Las pruebas se ejecutan en NetBeans antes de subir; aquí solo se empaqueta.
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
ENV TZ=America/Lima
WORKDIR /app
COPY --from=compilacion /app/target/autorent-1.0.0.jar app.jar
EXPOSE 8080
# Memoria moderada para los planes pequeños de Railway.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -jar app.jar"]
