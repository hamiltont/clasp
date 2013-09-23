TITLE=Clasp

LIB=../../../lib
CP=$(LIB)/hyperic-sigar-1.6.4/sigar-bin/lib/sigar.jar
CP:=$(CP):$(LIB)/akka-2.1.0/akka/slf4j-api-1.7.2.jar
CP:=$(CP):$(LIB)/akka-2.1.0/akka/akka-actor_2.10-2.1.0.jar
CP:=$(CP):$(LIB)/akka-2.1.0/akka/config-1.0.0.jar
CP:=$(CP):$(LIB)/commons-net-3.2/commons-net-3.2.jar

SRC_ROOT=../../../src
SRC=$(wildcard $(SRC_ROOT)/core/*.scala)
SRC+=$(wildcard $(SRC_ROOT)/core/sdktools/*.scala)

INFO=-doc-title $(TITLE) -doc-root-content root.wiki
FLAGS=-diagrams

all: index.html
index.html: $(SRC); scaladoc $(INFO) $(FLAGS) -classpath $(CP) $(SRC)
clean: ; rm -rf core index index.html index.js lib package.html
