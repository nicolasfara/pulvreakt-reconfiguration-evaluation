FROM eclipse-temurin:23

WORKDIR /experiment

COPY effects effects
COPY gradle gradle
COPY src src
COPY docker docker
COPY *.kts .
COPY *.properties .

RUN sed -i '/alias(libs.plugins.gitSemVer)/d' build.gradle.kts
COPY gradlew* .
RUN CI=true ./gradlew -PmaxTime=50 runAll --parallel
RUN rm -rf data
RUN ./gradlew --stop
CMD ./gradlew runAll
