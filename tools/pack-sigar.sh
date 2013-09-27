#!/bin/bash
#
# Packs the '.so' files from Sigar into the assembly jar.

function die { echo $@; exit 1; }

JAR=`find target -name "Clasp-Assembly.jar"`
[[ -e $JAR ]] || die "Unable to find '$JAR'"

# TODO Where should these libraries be packed?
SIGAR_LIB=lib/hyperic-sigar-1.6.4/sigar-bin/lib
jar uf $JAR -C $SIGAR_LIB .
