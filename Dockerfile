FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY . .

RUN chmod +x gradlew

RUN ./gradlew clean build -x test

CMD ["java", "-jar", "build/libs/uctale-0.0.1-SNAPSHOT.jar"]