FROM eclipse-temurin:11-jre
COPY target/*.jar app.jar
# catalog-service is the heaviest JVM in the stack (Hibernate + Spring Data Elasticsearch +
# Redis + Kafka + ModelMapper + SpringDoc + MinIO SDK/OkHttp4/Kotlin). The shared 128m
# Metaspace budget was sized before those deps and gets exhausted after hours of varied
# traffic (OutOfMemoryError: Metaspace -> intermittent 500s), so this service gets more headroom.
ENTRYPOINT ["java", "-Xms32m", "-Xmx192m", "-Xss256k", "-XX:MaxMetaspaceSize=256m", "-XX:CompressedClassSpaceSize=64m", "-XX:ReservedCodeCacheSize=48m", "-XX:MaxDirectMemorySize=32m", "-XX:+UseSerialGC", "-jar", "app.jar"]
EXPOSE 8084
