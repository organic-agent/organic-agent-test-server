# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 해석을 소스와 분리한다. src만 바뀌면 이 레이어는 캐시된다.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN ./gradlew dependencies --no-daemon --quiet || true

COPY src src
# 이미지 빌드 단계에서 테스트는 돌리지 않는다. Testcontainers가 도커를 요구하고,
# 테스트는 이미 CI에서 통과한 뒤다.
RUN ./gradlew bootJar --no-daemon


FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN useradd --system --user-group --create-home app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080

# 컨테이너 메모리 한도를 기준으로 힙을 잡는다. 안 주면 호스트 전체 메모리를 기준 삼는다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
