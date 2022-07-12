FROM jenkins/java:387404da3ce7

ENV MAVEN_HOME="/usr/share/maven"

# see https://www.jenkins.io/doc/book/using/using-agents/ and https://github.com/jenkinsci/docker-ssh-agent/issues/33
# probably, it would be better to use the official image docker-ssh-agent as a base
RUN VARS1="HOME=|USER=|MAIL=|LC_ALL=|LS_COLORS=|LANG=" \
    VARS2="HOSTNAME=|PWD=|TERM=|SHLVL=|LANGUAGE=|_=" \
    VARS="${VARS1}|${VARS2}" \
    env | egrep -v '^(${VARS})' >> /etc/environment