FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/config-server-*.jar app.jar
# Depot de configuration natif lu via file:./config-repo
COPY config-repo ./config-repo
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
