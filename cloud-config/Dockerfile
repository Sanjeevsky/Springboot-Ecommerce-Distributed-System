FROM openjdk:11
ADD target/*.jar configserver
ENTRYPOINT ["java", "-jar","configserver"]
EXPOSE 8071