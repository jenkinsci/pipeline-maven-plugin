FROM olamy/pipeline-maven-plugin:SshdContainer-32edfdd581112ff75ef6bbd6b0e576371f0089e3

RUN apt-get update && \
    apt-get install --no-install-recommends -y \
        software-properties-common \
        openjdk-11-jdk-headless \
        maven

ENV MAVEN_HOME="/usr/share/maven"

# see https://www.jenkins.io/doc/book/using/using-agents/ and https://github.com/jenkinsci/docker-ssh-agent/issues/33
# probably, it would be better to use the official image docker-ssh-agent as a base
RUN VARS1="HOME=|USER=|MAIL=|LC_ALL=|LS_COLORS=|LANG=" \
    VARS2="HOSTNAME=|PWD=|TERM=|SHLVL=|LANGUAGE=|_=" \
    VARS="${VARS1}|${VARS2}" \
    env | egrep -v '^(${VARS})' >> /etc/environment

ENV LANG en_US.UTF-8

# run SSHD in the foreground with error messages to stderr
ENTRYPOINT ["/usr/sbin/sshd", "-D", "-e"]