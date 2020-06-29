#Script to generate ELAN controlled vocabularies from the EASE ontology
#

# Usage:
# python ./elan_cv.py -of /path/to/outputfile.ecv -if /path/to/inputfile1.owl /path/to/inputfile2.owl ... -vi "vocabulary id string" -ns "namespace of start concepts" -sc StartConcept1 StartConcept2 ...

# Note that several -vi specifications can be made in the same call to the script. Each -vi must be followed by at least one -ns, and each -ns must be followed by one -sc.

# The script will crawl through the taxonomy of concepts in the input files. For each vocabulary id, the script starts with the list of concepts following the subsequent -sc parameter. The concept names should be bare, with no namespace. The namespace is defined by using an -ns parameter before the -sc list.

# Examples:

# python ./elan_cv.py -of ex1.ecv -if exin1.owl exin2.owl -vi "motions" -ns "https://example.org/exin.owl#" -sc Motion
# python ./elan_cv.py -of ex2.ecv -if exin1.owl exin2.owl -vi "motions" -ns "https://example.org/exin.owl#" -sc Motion -vi "places" -ns "https://example.org/exin.owl#" -sc Place Location
# python ./elan_cv.py -of ex3.ecv -if exin1.owl exin2.owl -vi "objects" -ns "https://ex.org/physical.owl#" -sc PhysicalObject -ns "https://ex.org/social.owl#" -sc SocialObject

#__author__ = "Mihai Pomarlan"
#__license__ = "GPL"
#__version__ = "1.0.0"
#__maintainer__ = "Mihai Pomarlan"
#__email__ = "pomarlan@uni-bremen.de"

import untangle
import sys
import os

import datetime
import argparse
import uuid
import hashlib
import itertools

cvPath = './elan_action_labels.txt'

DULAction = "http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#Action"

ease = "http://www.ease-crc.org/ont/EASE.owl#"
rdfs = "http://www.w3.org/2000/01/rdf-schema#"
rdf = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
owl = "http://www.w3.org/2002/07/owl#"
rdfs_subClassOf = rdfs + "subClassOf"
rdf_resource = rdf + "resource"
rdf_about = rdf + "about"
rdfs_comment = rdfs + "comment"
rdf_Description = rdf + "Description"
owl_Class = owl + "Class"
owl_Ontology = owl + "Ontology"
owl_versionInfo = owl + "versionInfo"
ELANName = ease + "ELANName"
ELANUsage = ease + "ELANUsageGuideline"

parser = argparse.ArgumentParser(description='Testing.')
parser.add_argument('--language_ref', '-lr', metavar='LR', type=str, help='a name to refer to the language by in the file, default is \"und\"', default='und')
parser.add_argument('--language_label', '-lb', metavar='LB', type=str, help='a short description of the language, default is the same value as the language_ref. An example description might be \"A set of words for objects.\"')
parser.add_argument('--output_file', '-of', metavar='OF', type=str, help='path to the output file', required=True)
parser.add_argument('--input_files', '-if', metavar='IF', type=str, required=True, nargs='+', help='paths to the input files')
parser.add_argument('--vocabulary_id', '-vi', metavar='LR', type=str, action='append', help='a human readable identifier for the vocabulary, default is \"und\"', required=True)
parser.add_argument('--namespace', '-ns', metavar='N', type=str, required=True, action='append', help='namespace to prepend to start concepts. An example namespace is \'http://www.ease-crc.org/ont/EASE.owl#\'')
parser.add_argument('--start_concepts', '-sc', metavar='C', type=str, required=True, nargs='+', action='append',
                    help='concepts to start from when creating the vocabulary; these and their subconcepts will be included. An example concept is Action')

args = parser.parse_args()

lang_ref = args.language_ref
lang_label = args.language_label
if not lang_label:
    lang_label = lang_ref

if not args.output_file:
    print('Need at least an output file. Call script as \n\tpython elan_cv.py -h for usage info\n')
    sys.exit(2)

cvPath = args.output_file

if not args.input_files:
    print('Need at least an input file. Call script as \n\tpython elan_cv.py -h for usage info\n')
    sys.exit(2)

paths = args.input_files

if not args.start_concepts:
    print('Need at least one concept to start crawling the ontology from. Call script as \n\tpython elan_cv.py -h for usage info\n')
    sys.exit(2)

if (not args.vocabulary_id) or (len(args.vocabulary_id) != len(args.start_concepts)):
    print('Need at least a vocabulary id per set of start concepts. Call script as \n\tpython elan_cv.py -h for usage info')

if (not args.namespace) or (len(args.namespace) != len(args.start_concepts)):
    print('Need one namespace per set of start concepts. Call script as \n\tpython elan_cv.py -h for usage info\n')
    sys.exit(2)

vocabs = {}

for vid, ns, scs in itertools.izip(args.vocabulary_id, args.namespace, args.start_concepts):
    if vid not in vocabs:
        vocabs[vid] = []
    for sc in scs:
        vocabs[vid].append(ns + sc)


def shaveOffNamespace(name):
    return name[name.rfind('#') + 1:]

def explicateOWLNamespace(name, namespaces, separator):
    sep = name.rfind(separator)
    if -1 == sep:
        return name
    namespace = name[:sep]
    nameSuffix = name[sep + 1:]
    if namespace in namespaces:
        name = namespaces[namespace] + nameSuffix
    return name

def explicateAttributeNamespace(name, namespaces):
    return explicateOWLNamespace(name, namespaces, ':')

def explicateNodeNamespace(name, namespaces):
    return explicateOWLNamespace(name, namespaces, '_')

def explicateValueNamespace(name, namespaces):
    if '&' == name[0]:
        return explicateOWLNamespace(name[1:], namespaces, ';')
    return name

def hasChild(owlNode, namespaces, childName):
    retq = []
    for c in owlNode.children:
        if childName == explicateNodeNamespace(c._name, namespaces):
            retq.append(c)
    return retq

def hasAttribute(owlNode, namespaces, attributeName):
    for k, v in owlNode._attributes.items():
        k = explicateAttributeNamespace(k, namespaces)
        if k == attributeName:
            return v, True
    return None, False

def gatherNamespaces(onto):
    retq = {}
    for k, v in onto.rdf_RDF._attributes.items():
        split = k.rfind(':')
        if -1 != split:
            kp = k[:split]
            ks = k[split + 1:]
            if 'xmlns' == kp.lower():
                retq[ks] = v
    return retq

def parseXMLOnto(onto):
    try:
        ontoP = untangle.parse(onto)
        if 'rdf_RDF' not in dir(ontoP):
            print("Error: this file doesn't seem to be in RDF/XML format: %s" % onto)
            sys.exit(1)
        namespaces = gatherNamespaces(ontoP)
        return ontoP, namespaces
    except IOError:
        print("Error: couldn't read file: %s" % onto)
        sys.exit(1)
    
def getParents(owlClass, namespaces):
    parents = hasChild(owlClass, namespaces, rdfs_subClassOf)
    retq = set([])
    for parent in parents:
        resource, hasIt = hasAttribute(parent, namespaces, rdf_resource)
        if hasIt:
            retq.add(explicateValueNamespace(resource, namespaces))
    return retq

def getName(owlClass, namespaces):
    name, hasIt = hasAttribute(owlClass, namespaces, rdf_about)
    if not hasIt:
        print("Warning: class node with no name detected")
        return ""
    return explicateValueNamespace(name, namespaces)

def getELANName(owlNode, namespaces):
    elan = hasChild(owlNode, namespaces, ELANName)
    if not elan:
        elan = hasChild(owlNode, namespaces, 'ELANName')
    if not elan:
        return [shaveOffNamespace(getName(owlNode, namespaces))], False
    retq = []
    for n in elan:
        retq.append(str(n.cdata))
    return retq, True

def getDescription(owlNode, namespaces):
    description = hasChild(owlNode, namespaces, ELANUsage)
    retq = []
    for d in description:
        retq.append(str(d.cdata))
    return retq

def gatherActions(onto, actionClassNames, namespaces):
    retq = {}
    owlClasses = hasChild(onto.rdf_RDF, namespaces, owl_Class)
    for owlClass in owlClasses:
        parents = getParents(owlClass, namespaces)
        actionParents = actionClassNames.intersection(parents)
        if actionParents:
            owlClassName = getName(owlClass, namespaces)
            elanName, haveELANName = getELANName(owlClass, namespaces)
            description = getDescription(owlClass, namespaces)
            if owlClassName not in retq:
                retq[owlClassName] = {'parents': set([]), 'ELANName': elanName, 'description': description}
            retq[owlClassName]['parents'] = retq[owlClassName]['parents'].union(parents)
    return retq

def makeHTMLAttributeSafe(string):
    string = string.replace('\n', '')
    string = string.replace('\r', '')
    string = string.replace('\t', '')
    string = string.replace('\"', '\'')
    return string

vocab_words = []
for cv_id, actionParents in vocabs.items():
    actionParents = set(actionParents)
    ontos = paths
    for onto in ontos:
        if not os.path.isfile(onto):
            print("Error, file does not exist: %s" % onto)
            sys.exit(1)
    version = ""
    actionClasses = {}
    # Gather version strings
    for onto in ontos:
        adds = str(onto[onto.rfind('/') + 1:]) + " 0.0.0 | "
        ontoP, namespaces = parseXMLOnto(onto)
        owlOntology = hasChild(ontoP.rdf_RDF, namespaces, owl_Ontology)
        if owlOntology:
            owlVersionInfo = hasChild(owlOntology[0], namespaces, owl_versionInfo)
            if owlVersionInfo:
                adds = str(owlVersionInfo[0].cdata) + " | "
        version += adds
    # Gather actions
    while actionParents:
        newActionClasses = {}
        for onto in ontos:
            ontoP, namespaces = parseXMLOnto(onto)
            newActionClasses.update(gatherActions(ontoP, actionParents, namespaces))
        actionParents = set(newActionClasses.keys())
        actionClasses.update(newActionClasses)
    # Gather 'descriptions' for actions
    for onto in ontos:
        ontoP, namespaces = parseXMLOnto(onto)
        rdfDescription = hasChild(ontoP.rdf_RDF, namespaces, rdf_Description)
        for d in rdfDescription:
            name, hasIt = hasAttribute(d, namespaces, rdf_about)
            if hasIt:
                name = explicateValueNamespace(name, namespaces)
                if name in actionClasses:
                    actionClasses[name]['description'] += getDescription(d, namespaces)
                    elanName, haveName = getELANName(d, namespaces)
                    if haveName:
                        actionClasses[name]['ELANName'] = elanName + actionClasses[name]['ELANName']
    # Classes may have all sorts of parents, but we only need the parents that are also actions
    actionClassNames = set(actionClasses.keys())
    for actName, act in actionClasses.items():
        act['parents'] = act['parents'].intersection(actionClassNames)
    # Construct the list of words in the controlled vocabulary
    words = []
    for actName, act in actionClasses.items():
        if 2 < len(act['ELANName']):
            print("Warning: more than 2 ELAN Names configured for action %s" % actName)
        words.append({'ELANName': act['ELANName'][0], 'parents': act['parents'], 'description': act['description']})
    words.sort(key=lambda dic: dic['ELANName'])
    vocab_words.append((cv_id, words))

vocab_words.sort(key=lambda p: p[0])

with open(cvPath, 'w') as outfile:
    outfile.write('<?xml version="1.0" encoding="UTF-8"?>\n')
    outfile.write('<CV_RESOURCE AUTHOR=\"\" DATE=\"%s\" VERSION=\"%s\"\n\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://www.mpi.nl/tools/elan/EAFv2.8.xsd\">\n' % (str(datetime.datetime.now()), version))
    outfile.write('\t<LANGUAGE LANG_DEF="http://cdb.iso.org/lg/CDB-00130975-001" LANG_ID="%s" LANG_LABEL="%s"/>\n' % (lang_ref, lang_label))
    for cv_id, words in vocab_words:
        outfile.write('\t<CONTROLLED_VOCABULARY CV_ID="%s">\n' % (cv_id))
        outfile.write('\t\t<DESCRIPTION LANG_REF="%s"/>\n' % (lang_ref,))
        for word in words:
            outfile.write('\t\t<CV_ENTRY_ML CVE_ID="%s">\n' % ("cveid_"+ str(hashlib.md5((lang_ref + cv_id + word['ELANName']).encode('utf-8')).hexdigest())))
            # outfile.write('\t\t<CV_ENTRY_ML CVE_ID="%s">\n' % ("cveid_"+ str(uuid.uuid4()),))
            desc = str(word['description'])
            if 1 == len(word['description']):
                desc = str(word['description'][0])
            outfile.write('\t\t\t<CVE_VALUE DESCRIPTION="%s" LANG_REF="%s">%s</CVE_VALUE>\n' % (makeHTMLAttributeSafe(desc), lang_ref, word['ELANName']))
            outfile.write('\t\t</CV_ENTRY_ML>\n')
        outfile.write('\t</CONTROLLED_VOCABULARY>\n')
    outfile.write('</CV_RESOURCE>\n')

