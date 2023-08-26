FROM localhost/pipeline-maven/java-maven-git

COPY --from=eclipse-temurin:8u382-b05-jdk /opt/java/openjdk /opt/java/jdk8
COPY --from=eclipse-temurin:11.0.20.1_1-jdk /opt/java/openjdk /opt/java/jdk11
COPY --from=eclipse-temurin:17.0.8.1_1-jdk /opt/java/openjdk /opt/java/jdk17
