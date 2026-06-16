FROM bellsoft/liberica-openjre-alpine:11
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-Xms32m", "-Xmx96m", "-Xss256k", "-XX:MaxMetaspaceSize=128m", "-XX:CompressedClassSpaceSize=32m", "-XX:ReservedCodeCacheSize=24m", "-XX:MaxDirectMemorySize=32m", "-XX:+UseSerialGC", "-jar", "app.jar"]
EXPOSE 9000
