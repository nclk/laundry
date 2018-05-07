#FROM pritunl/archlinux:2016-10-01
FROM archlinux/base

WORKDIR /laundry

RUN pacman -Syyu \
  sudo \
  git \
  jre8-openjdk \
  python \
  python-pip \
  postgresql \
  --noconfirm

RUN curl -o /bin/lein \
        https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
RUN chmod 755 /bin/lein

ADD . /laundry
RUN lein ring uberjar

CMD ["java","-jar","target/sut-0.1.0-SNAPSHOT-standalone.jar"]
#CMD ["bash", "bin/initialize.sh"]

