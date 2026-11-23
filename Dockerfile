FROM docker.io/library/eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src ./src
RUN ./mvnw -q -DskipTests package && mv target/*.jar target/app.jar

FROM docker.io/library/eclipse-temurin:17-jre AS run
RUN useradd --system --no-create-home bulkimport
# created before USER so a fresh named volume mounted here starts out owned by bulkimport
RUN mkdir -p /app/data/uploads && chown bulkimport /app/data/uploads
USER bulkimport
WORKDIR /app
ENV IMPORT_STORAGE_DIR=/app/data/uploads
COPY --from=build /app/target/app.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
