FROM ubuntu:noble as base

# install SSHD
RUN apt-get update -y && \
    apt-get install -y \
        openssh-server \
        locales
RUN mkdir -p /var/run/sshd

# create a test user
RUN useradd test -d /home/test && \
    mkdir -p /home/test/.ssh && \
    echo 'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDzpxmTW9mH87DMkMSqBrSecoSHVCkKbW5IOO+4unak8M8cyn+b0iX07xkBn4hUJRfKA7ezUG8EX9ru5VinteqMOJOPknCuzmUS2Xj/WJdcq3BukBxuyiIRoUOXsCZzilR/DOyNqpjjI3iNb4los5//4aoKPCmLInFnQ3Y42VaimH1298ckEr4tRxsoipsEAANPXZ3p48gGwOf1hp56bTFImvATNwxMViPpqyKcyVaA7tXCBnEk/GEwb6MiroyHbS0VvBz9cZOpJv+8yQnyLndGdibk+hPbGp5iVAIsm28FEF+4FvlYlpBwq9OYuhOCREJvH9CxDMhbOXgwKPno9GyN kohsuke@atlas' > /home/test/.ssh/authorized_keys && \
    echo 'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDagNSCDst/8z5oH9S5QWr+QNdx+haImY0FD3IQvKdD+eWI9zUbBgtoo/yYEbLvpTWiKsgT3Hw1F8mZ+/bd2Uv3lPyoG+TSzrHL4gSal6d1RWVjCOzSosciXVm4gRUvJjKXzaz8dOg+ii9yIrbeONNK0nlDUCAKy5YXSEl0avcPdUDyR3cStL6870SyanxAzktDw0n8xMq4F/alF3PZ002bcZJrmDeNVAwkP+uO2Tf8pN37SU+nApotZmlmZR32xYHnx+/OiQ7gOAVYmgNRMg0Kwh6Q73FcY3ZWCeNHwLnr95LoEAdj3On8Qr62VhGThuQNVCqBc6SeYjArfjijpcW9 jenkins-ci@localhost' >> /home/test/.ssh/authorized_keys && \
    chown -R test:test /home/test && \
    chmod 0600 /home/test/.ssh/authorized_keys && \
    echo "test:test" | chpasswd

# https://stackoverflow.com/a/38553499/12916
RUN sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen && \
    dpkg-reconfigure --frontend=noninteractive locales && \
    update-locale LANG=en_US.UTF-8
ENV LANG en_US.UTF-8

# run SSHD in the foreground with error messages to stderr
ENTRYPOINT ["/usr/sbin/sshd", "-D", "-e"]

FROM base as java-git

RUN apt-get update && \
    apt-get install --no-install-recommends -y \
        software-properties-common \
        openjdk-17-jdk-headless \
        curl \
        ant \
        git

FROM java-git as java-maven-git

RUN apt-get update && \
    apt-get install --no-install-recommends -y \
        maven

FROM java-maven-git as javas

COPY --from=eclipse-temurin:8u472-b08-jdk /opt/java/openjdk /opt/java/jdk8
COPY --from=eclipse-temurin:11.0.30_7-jdk /opt/java/openjdk /opt/java/jdk11
COPY --from=eclipse-temurin:17.0.18_8-jdk /opt/java/openjdk /opt/java/jdk17
COPY --from=eclipse-temurin:21.0.9_10-jdk  /opt/java/openjdk /opt/java/jdk21
COPY --from=eclipse-temurin:25.0.1_8-jdk  /opt/java/openjdk /opt/java/jdk25

FROM java-maven-git as maven-home

ENV MAVEN_HOME="/usr/share/maven"

# see https://www.jenkins.io/doc/book/using/using-agents/ and https://github.com/jenkinsci/docker-ssh-agent/issues/33
# probably, it would be better to use the official image docker-ssh-agent as a base
RUN VARS1="HOME=|USER=|MAIL=|LC_ALL=|LS_COLORS=|LANG=" \
    VARS2="HOSTNAME=|PWD=|TERM=|SHLVL=|LANGUAGE=|_=" \
    VARS="${VARS1}|${VARS2}" \
    env | egrep -v '^(${VARS})' >> /etc/environment


