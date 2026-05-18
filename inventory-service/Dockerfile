FROM openjdk:11
ADD target/*.jar inventory-service.jar
ENTRYPOINT ["java", "-jar", "inventory-service.jar"]
EXPOSE 8088
