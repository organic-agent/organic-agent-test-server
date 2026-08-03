FROM eclipse-temurin:21-jre

# 로그 타임스탬프와 LocalDateTime.now()가 이 값을 따른다.
ENV TZ=Asia/Seoul

# 이미지 안에서 gradle을 돌리지 않는다. CI가 만든 jar를 넣기만 한다.
# RUN 스텝이 없어야 amd64 러너에서 arm64 이미지를 QEMU 없이 빌드할 수 있다.
ARG JAR_FILE=build/libs/test-wes-0.0.1-SNAPSHOT.jar

COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]
