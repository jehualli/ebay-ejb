# TomEE 8 "plus" = Tomcat + OpenEJB, the full EJB container, still on the
# javax.* namespace. Same servlet engine as before, with a business tier
# alongside it, so both containers can be compared inside one deployment.
#
# TomEE ships a JRE only, so the Java half is compiled in a JDK stage against
# TomEE's own jars -- servlet-api, jsp-api and the EJB API in one javaee-api jar.
FROM --platform=linux/amd64 tomee:8-jre8-plus AS tomee

FROM --platform=linux/amd64 eclipse-temurin:8-jdk AS build
COPY --from=tomee /usr/local/tomee/lib /tomee-lib
COPY src /build/src
RUN mkdir -p /out \
 && find /build/src -name '*.java' > /build/sources.txt \
 && javac -nowarn -encoding ISO-8859-1 -cp "/tomee-lib/*" -d /out @/build/sources.txt \
 && find /out -name '*.class' | wc -l

FROM --platform=linux/amd64 tomee:8-jre8-plus
RUN rm -rf /usr/local/tomee/webapps/*
COPY webapp/ /usr/local/tomee/webapps/ROOT/
COPY --from=build /out/ /usr/local/tomee/webapps/ROOT/WEB-INF/classes/
EXPOSE 8080
