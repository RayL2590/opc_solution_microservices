# Build dans le conteneur, pas sur l'hôte : l'image de build fige Maven 3.9 + JDK 17, donc le
# jar est le même quelle que soit la machine, et `docker build` suffit — pas de Maven ni de JDK
# à installer au préalable. L'image de build est jetée ; seuls les layers extraits partent dans
# l'image finale (runtime JRE alpine).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests
# -exec.jar explicitement : le build installe aussi le jar simple, donc target/*.jar est ambigu.
RUN java -Djarmode=layertools -jar target/*-exec.jar extract --destination extracted

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
RUN chown -R spring:spring /app
USER spring
EXPOSE 8082
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
