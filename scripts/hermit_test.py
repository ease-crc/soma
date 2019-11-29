#!/usr/bin/env python
import os
import sys
from optparse import OptionParser

import subprocess
import re

usage = "usage: %prog [options] inputfile"
parser = OptionParser(usage)
parser.add_option("-o", "--output", dest="output", default=None,
                  help="output FILE", metavar="OUTPUT")
parser.add_option("-r", "--reasoner", dest="reasoner", metavar="REASONER", default="./HermiT.jar",
                  help="path to REASONER (assumed to be HermiT.jar)")

(options, args) = parser.parse_args()

if 1 != len(args):
    parser.error("incorrect number of arguments")

try:
    proc = subprocess.Popen(["java", "-Xmx1024M", "-jar", options.reasoner, "--classify", "--classifyOPs", args[0]], stdout = subprocess.PIPE, stderr = subprocess.PIPE)
    stdout, stderr = proc.communicate()
except OSError as e:
    sys.exit("ERROR: failed to execute seq: %s" % (str(e)))

if stderr:
    print("ERROR: reasoner failed to complete classification query\n%s" % str(stderr))
    sys.exit()

results = stdout.decode()

if options.output:
    with open(options.output, "w") as outfile:
        outfile.write(results)

subsumptions = results.splitlines()

owlNothing = re.compile("owl\\#Nothing[\\>]?")
owlBottomObjectProperty = re.compile("owl\\#bottomObjectProperty[\\>]?")

equivalentClasses = re.compile('^EquivalentClasses\\( (?P<A>[\\w\\#\\<\\>:/\\-\\.]*) (?P<B>[\\w\\#\\<\\>:/\\-\\.]*) \\)$')
equivalentObjectProperties = re.compile('^EquivalentObjectProperties\\( (?P<A>[\\w\\#\\<\\>:/\\-\\.]*) (?P<B>[\\w\\#\\<\\>:/\\-\\.]*) \\)$')
subClassOf = re.compile('^SubClassOf\\( (?P<sub>[\\w\\#\\<\\>:/\\-\\.]*) (?P<sup>[\\w\\#\\<\\>:/\\-\\.]*) \\)$')
subObjectPropertyOf = re.compile('^SubObjectPropertyOf\\( (?P<sub>[\\w\\#\\<\\>:/\\-\\.]*) (?P<sup>[\\w\\#\\<\\>:/\\-\\.]*) \\)$')

for s in subsumptions:
    s = s.strip()
    if not s:
        continue
    isEquivalentClass = equivalentClasses.match(s)
    isEquivalentProperty = equivalentObjectProperties.match(s)
    isSubclass = subClassOf.match(s)
    isSubproperty = subObjectPropertyOf.match(s)
    if isEquivalentClass:
        AisNothing = owlNothing.search(isEquivalentClass.group('A'))
        BisNothing = owlNothing.search(isEquivalentClass.group('B'))
        if AisNothing:
            print("WARNING: %s is subclass of Nothing" % isEquivalentClass.group('B'))
        if BisNothing:
            print("WARNING: %s is subclass of Nothing" % isEquivalentClass.group('A'))
    elif isEquivalentProperty:
        AisNothing = owlBottomObjectProperty.search(isEquivalentProperty.group('A'))
        BisNothing = owlBottomObjectProperty.search(isEquivalentProperty.group('B'))
        if AisNothing:
            print("WARNING: %s is subproperty of bottomObjectProperty" % isEquivalentProperty.group('B'))
        if BisNothing:
            print("WARNING: %s is subproperty of bottomObjectProperty" % isEquivalentProperty.group('A'))
    elif isSubclass:
        SupisNothing = owlNothing.search(isSubclass.group('sup'))
        if SupisNothing:
            print("WARNING: %s is subclass of Nothing" % isSubclass.group('sub'))
    elif isSubproperty:
        SupisNothing = owlBottomObjectProperty.search(isSubproperty.group('sup'))
        if SupisNothing:
            print("WARNING: %s is subproperty of bottomObjectProperty" % isSubproperty.group('sub'))
    else:
        print("ERROR: %s" % s)
