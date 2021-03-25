FROM openjdk:11-jdk
EXPOSE 8080:8080
RUN mkdir /app
COPY ./build/install/web/ /app/
WORKDIR /app/bin
CMD ["./web"]