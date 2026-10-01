FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system conflux && useradd --system --gid conflux --no-create-home conflux
COPY --from=build /app/target/backend-*.jar app.jar
USER conflux
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
