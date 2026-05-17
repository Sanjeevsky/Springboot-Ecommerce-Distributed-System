FROM openjdk:11
ADD target/*.jar api-gateway
ENTRYPOINT ["java", "-jar","api-gateway"]
EXPOSE 8081