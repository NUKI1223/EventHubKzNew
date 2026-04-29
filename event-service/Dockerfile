FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-Dnetworkaddress.cache.ttl=60", "-jar", "app.jar"]
