FROM openjdk:11
RUN mkdir -p /app
WORKDIR /app
COPY target/backend-0.0.1-SNAPSHOT-standalone.jar .
CMD java -jar backend-0.0.1-SNAPSHOT-standalone.jar
EXPOSE 8000
