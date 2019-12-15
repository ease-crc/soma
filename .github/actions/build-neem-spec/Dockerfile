FROM blang/latex:ubuntu

RUN apt-get update \
    && apt-get install -y curl \
    && mkdir /build \
    && curl -o template.zip -L https://resource-cms.springernature.com/springer-cms/rest/v1/content/20568/data/v8 \
    && unzip -d /template template.zip
WORKDIR /build
COPY entrypoint.sh /entrypoint.sh

ENV TEXINPUTS /template:
ENTRYPOINT /entrypoint.sh
