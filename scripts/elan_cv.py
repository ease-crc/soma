import untangle
import sys
import os

cvPath = '../owl/elan_action_labels.txt'

DULAction = "http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#Action"

ease = "http://www.ease.org/ont/EASE.owl#"
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

argc = len(sys.argv)
paths = []

if 1 < argc:
    cvPath = sys.argv[1]
else:
    print('Need at least an output file. Call script as \n\tpython elan_cv.py outfilepath [infile1 infile2 ...]\n')
    sys.exit(2)
if 2 < argc:
    paths = sys.argv[2:]

#TODO: format output into a proper ELAN cv file

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
    description = hasChild(owlNode, namespaces, rdfs_comment)
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

ontos = paths

for onto in ontos:
    if not os.path.isfile(onto):
        print("Error, file does not exist: %s" % onto)
        sys.exit(1)

version = ""

actionParents = set([DULAction])
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

# TODO: format output appropriately

words.sort(key=lambda dic: dic['ELANName'])

with open(cvPath, 'w') as outfile:
    outfile.write('Version %s\n' % version)
    outfile.write('===\n')
    for word in words:
        outfile.write('%s\n' % word['ELANName'])
        outfile.write('Parents: %s\n' % str(word['parents']))
        outfile.write('%s\n' % str(word['description']))
        outfile.write('========\n')

