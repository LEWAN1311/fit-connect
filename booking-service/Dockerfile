FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/booking-service-*.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
