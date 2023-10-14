FROM localhost/pipeline-maven/java-maven-git

COPY --from=eclipse-temurin:8u382-b05-jdk /opt/java/openjdk /opt/java/jdk8
COPY --from=eclipse-temurin:11.0.20.1_1-jdk /opt/java/openjdk /opt/java/jdk11
COPY --from=eclipse-temurin:17.0.8.1_1-jdk /opt/java/openjdk /opt/java/jdk17

RUN set -e; \
    wget -q -O /tmp/openjdk.tar.gz https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21%2B35-ea-beta/OpenJDK21U-jdk_x64_linux_hotspot_ea_21-0-35.tar.gz; \
    echo "b6586c432948134387b801add3b46048e3fb58390d74eea80e00c539016a54e6 */tmp/openjdk.tar.gz" | sha256sum -c -; \
    mkdir -p "/opt/java/jdk21"; \
    tar --extract \
        --file /tmp/openjdk.tar.gz \
        --directory "/opt/java/jdk21" \
        --strip-components 1 \
        --no-same-owner \
    ; \
    rm -f /tmp/openjdk.tar.gz /opt/java/jdk21/lib/src.zip;
