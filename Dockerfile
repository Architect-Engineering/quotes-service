# Use Managed Base Image Oracle JDK 11
FROM openjdk:8-jre-alpine3.9

# Human-readable title of the image (string)
LABEL org.opencontainers.image.title="quotes-service"

ARG HARNESS_URL

LABEL harness.url="${HARNESS_URL}"

# Default the target version to 0.0.0-SNAPSHOT
ARG gavVersion=0.5.0

# Override the version from environment, if present (helpful with CI tools)
ENV gavVersion ${gavVersion}

#RUN cd harness/build/libs; ls -ltr

# Copy the already build jar to the image
COPY build/libs/*.jar /app/quotes-service.jar

# Expose default port for external communication
EXPOSE 8083

# Command to run the executable
ENTRYPOINT [ "java" ,"-jar",  "/app/quotes-service.jar" ]

#CMD java $JAVA_OPTS -cp /bin/ org.springframework.boot.loader.JarLauncher
