FROM openjdk:11
ADD target/*.jar notification-service.jar
ENTRYPOINT ["java", "-jar", "notification-service.jar"]
EXPOSE 8087
