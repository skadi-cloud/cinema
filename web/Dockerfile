FROM openjdk:19-jdk
EXPOSE 8080:8080
RUN mkdir /app
ARG release
ENV SENTRY_RELEASE=${release}
COPY ./build/install/web/ /app/
WORKDIR /app/bin
CMD ["./web"]