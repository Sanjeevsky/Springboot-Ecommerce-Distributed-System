FROM openjdk:11
ADD target/*.jar service-discovery
ENTRYPOINT ["java", "-jar","service-discovery"]
EXPOSE 8761