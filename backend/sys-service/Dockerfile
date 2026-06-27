FROM --platform=linux/amd64 eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/
COPY sys-service/pom.xml sys-service/
COPY scm-service/pom.xml scm-service/
COPY wms-service/pom.xml wms-service/
COPY recipe-service/pom.xml recipe-service/
COPY cook-service/pom.xml cook-service/
COPY sample-service/pom.xml sample-service/
COPY health-service/pom.xml health-service/
COPY device-service/pom.xml device-service/

COPY common/src common/src
COPY sys-service/src sys-service/src

RUN apk add --no-cache maven && \
    mvn install -DskipTests -pl sys-service -am -B

FROM --platform=linux/amd64 eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/sys-service/target/*.jar app.jar

EXPOSE 8089

ENTRYPOINT ["java", "-jar", "app.jar"]
