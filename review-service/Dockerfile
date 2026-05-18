FROM openjdk:11
ADD target/*.jar review-service.jar
ENTRYPOINT ["java", "-jar", "review-service.jar"]
EXPOSE 8090
