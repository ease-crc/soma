import untangle
import sys

cvPath = '../owl/elan_action_labels.txt'

argc = len(sys.argv)
paths = []

if 1 < argc:
    cvPath = sys.argv[1]
else:
    print('Need at least an output file. Call script as \n\tpython elan_cv.py outfilepath [infile1 infile2 ...]\n')
    sys.exit()
if 2 < argc:
    paths = sys.argv[2:]
    
def shaveOffNamespace(name):
    return name[name.rfind('#') + 1:]

def explicateNamespace(name, namespaces):
    if '&' == name[0]:
        namespace = name[:name.rfind(';') + 1]
        nameSuffix = name[name.rfind(';') + 1:]
        if namespace in namespaces:
            name = namespaces[namespace] + '' + nameSuffix
    return name

def getParents(owlClass, namespaces):
    if 'rdfs_subClassOf' not in dir(owlClass):
        return set([])
    parents = owlClass.rdfs_subClassOf
    if isinstance(parents, untangle.Element):
        parents = [parents]
    retq = set([])
    for parent in parents:
        if parent['rdf:resource']:
            retq.add(explicateNamespace(parent['rdf:resource'], namespaces))
    return retq

def getName(owlClass, namespaces):
    return explicateNamespace(owlClass['rdf:about'], namespaces)

def getELANName(owlClass, namespaces):
    if 'ELANName' in dir(owlClass):
        elan = owlClass.ELANName
        if not isinstance(elan, list):
            elan = [elan]
        retq = []
        for n in elan:
            retq.append(str(n.cdata))
        return retq
    return [shaveOffNamespace(getName(owlClass, namespaces))]

def getDescription(owlClass):
    if 'rdfs_comment' not in dir(owlClass):
        return []
    description = owlClass.rdfs_comment
    if not isinstance(description, list):
        description = [description]
    retq = []
    for d in description:
        retq.append(str(d.cdata))
    return retq

def gatherActions(onto, actionClassNames, namespaces):
    retq = {}
    for owlClass in onto.rdf_RDF.owl_Class:
        parents = getParents(owlClass, namespaces)
        actionParents = actionClassNames.intersection(parents)
        if actionParents:
            owlClassName = getName(owlClass, namespaces)
            elanName = getELANName(owlClass, namespaces)
            description = getDescription(owlClass)
            if owlClassName not in retq:
                retq[owlClassName] = {'parents': set([]), 'ELANName': elanName, 'description': description}
            retq[owlClassName]['parents'] = retq[owlClassName]['parents'].union(parents)
    return retq

def gatherNamespaces(onto):
    retq = {}
    for k, v in onto.rdf_RDF._attributes.items():
        split = k.rfind(':')
        if -1 != split:
            kp = k[:split]
            ks = k[split + 1:]
            if 'xmlns' == kp.lower():
                retq['&'+ks+';'] = ks
    return retq

ontos = paths

version = ""

DULAction = "http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#Action"

actionParents = set([DULAction])
actionClasses = {}

for onto in ontos:
    ontoP = untangle.parse(onto)
    if 'owl_versionInfo' in dir(ontoP.rdf_RDF.owl_Ontology):
        version += str(ontoP.rdf_RDF.owl_Ontology.owl_versionInfo.cdata) + " | "
    else:
        version += str(onto[onto.rfind('/') + 1:]) + " 0.0.0 | "

while actionParents:
    newActionClasses = {}
    for onto in ontos:
        ontoP = untangle.parse(onto)
        namespaces = gatherNamespaces(ontoP)
        newActionClasses.update(gatherActions(ontoP, actionParents, namespaces))
    actionParents = set(newActionClasses.keys())
    actionClasses.update(newActionClasses)

actionClassNames = set(actionClasses.keys())
for actName, act in actionClasses.items():
    act['parents'] = act['parents'].intersection(actionClassNames)

words = []
for actName, act in actionClasses.items():
    words.append({'ELANName': act['ELANName'][0], 'parents': act['parents'], 'description': act['description']})

words.sort(key=lambda dic: dic['ELANName'])

with open(cvPath, 'w') as outfile:
    outfile.write('Version %s\n' % version)
    outfile.write('===\n')
    for word in words:
        outfile.write('%s\n' % word['ELANName'])
        outfile.write('Parents: %s\n' % str(word['parents']))
        outfile.write('%s\n' % str(word['description']))
        outfile.write('========\n')

