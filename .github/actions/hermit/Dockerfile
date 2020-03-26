FROM maven:3.6.1-jdk-8-slim

RUN apt-get update \
    && apt-get install -y git
RUN git clone https://github.com/owlcs/hermit-reasoner.git

WORKDIR hermit-reasoner

# checkout Release 1.4.4.519
RUN git checkout 57b79367f2b66f0b510598474efbde43195410cc

# Apply patch to build standalone jar file
COPY Adding-assembly-target.patch Adding-assembly-target.patch
RUN git apply Adding-assembly-target.patch

# Install HermiT, skip tests
RUN mvn clean install -DskipTests=true

WORKDIR /github/workspace
ENTRYPOINT [\
    "java", \
    "-Xmx1024M", \
    "-jar", "/hermit-reasoner/target/org.semanticweb.hermit-1.4.4.519-jar-with-dependencies.jar", \
    "--output", "hermit.output", \
    "--classify", \
    "--classifyOPs" \
]
CMD ["owl/EASE-UGLY.owl"]
