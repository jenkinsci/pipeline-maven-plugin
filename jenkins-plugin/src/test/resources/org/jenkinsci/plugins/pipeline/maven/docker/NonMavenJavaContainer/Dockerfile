#
# Runs sshd and allow the 'test' user to login
#

FROM olamy/pipeline-maven-plugin:SshdContainer-32edfdd581112ff75ef6bbd6b0e576371f0089e3

RUN apt-get update && \
    apt-get install --no-install-recommends -y \
        software-properties-common \
        openjdk-11-jdk-headless

# run SSHD in the foreground with error messages to stderr
ENTRYPOINT ["/usr/sbin/sshd", "-D", "-e"]

