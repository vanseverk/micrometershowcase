FROM adoptopenjdk/openjdk11:latest
RUN mkdir /opt/app
ADD ./target/cafeservice-0.0.1-SNAPSHOT.jar /opt/app/app.jar
EXPOSE 8080
CMD ["java", "-jar", "/opt/app/app.jar"]