ARG ECR_HOST
FROM ${ECR_HOST}/rcx-java-base

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
    java-11-amazon-corretto-jdk \
    locales \
  && rm -rf /var/lib/apt/lists/*
  
RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en

WORKDIR /home/rcx
COPY --chown=rcx:rcx ./target/rcx-event-publisher-*.jar ./rcx-event-publisher.jar
ADD --chown=rcx:rcx ./thirdparty/newrelic ./newrelic
ADD --chown=rcx:rcx https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/aws-opentelemetry-agent.jar /opt/aws-opentelemetry-agent.jar
ADD --chown=rcx:rcx ./SBI/docker/rcx-entry.keystore ./.keystore
ADD --chown=rcx:rcx ./SBI/docker/rcx-entry.env-vars ./.env-vars

USER rcx

ENTRYPOINT [ "/rcx-entry" ]
CMD [ "java", "-jar", "rcx-event-publisher.jar"]
EXPOSE 9090 8080
