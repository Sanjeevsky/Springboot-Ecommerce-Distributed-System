FROM eclipse-temurin:11-jre
ADD target/*.jar app.jar
ENTRYPOINT ["java", "-Xms32m", "-Xmx128m", "-Xss256k", "-XX:MaxMetaspaceSize=128m", "-XX:ReservedCodeCacheSize=32m", "-XX:MaxDirectMemorySize=64m", "-XX:+UseSerialGC", "-jar", "app.jar"]
EXPOSE 8086
