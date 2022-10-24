#!/usr/bin/env python
import argparse
import re
import os
import sys
from pathlib import Path


OWL_NOTHING = 'http://www.w3.org/2002/07/owl#Nothing'
BOTTOM_OBJECT_PROPERTY = 'http://www.w3.org/2002/07/owl#bottomObjectProperty'

## IRI = '<(.*?)>'

## EQUIVALENT_CLASSES = r'^EquivalentClasses\( (.*) \)$'
## OBJECT_INVERSE_OF = r'ObjectInverseOf\( ' + IRI + r' \)'
## EQUIVALENT_OBJECT_PROPERTIES = r'^EquivalentObjectProperties\( (.*) \)$'

def _parseHomebrew(fileText):
    inEq = False
    nullClasses = []
    aux = []
    iriPLen = len("<Class IRI=\"")
    for l in fileText.splitlines():
        l = l.strip()
        if inEq:
            if "</EquivalentClasses>" == l:
                inEq = False
                if OWL_NOTHING in aux:
                    nullClasses = nullClasses + aux
                aux = []
            else:
                if l.startswith("<Class IRI=\""):
                    aux.append(l[iriPLen:-3])
        else:
            if "<EquivalentClasses>" == l:
                inEq = True
    nullClasses = list(set(nullClasses))
    if OWL_NOTHING in nullClasses:
        nullClasses.remove(OWL_NOTHING)
    return nullClasses

def makeObjectPropertyQueryFile(objProps, somaPath):
    queryMap = {}
    queryOwl = './SOMA_QUERY.owl'
    with open(queryOwl, 'w') as outfile:
        outfile.write('Prefix(:=<http://www.ease-crc.org/ont/SOMA_QUERY.owl#>)\n')
        outfile.write('Prefix(dul:=<http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#>)\n')
        outfile.write('Prefix(owl:=<http://www.w3.org/2002/07/owl#>)\n')
        outfile.write('Prefix(rdf:=<http://www.w3.org/1999/02/22-rdf-syntax-ns#>)\n')
        outfile.write('Prefix(xml:=<http://www.w3.org/XML/1998/namespace>)\n')
        outfile.write('Prefix(xsd:=<http://www.w3.org/2001/XMLSchema#>)\n')
        outfile.write('Prefix(rdfs:=<http://www.w3.org/2000/01/rdf-schema#>)\n')
        outfile.write('Prefix(soma:=<http://www.ease-crc.org/ont/SOMA.owl#>)\n\n\n')
        outfile.write('Ontology(<http://www.ease-crc.org/ont/SOMA_QUERY.owl>\n')
        outfile.write('Import(<file://%s>)\n' % somaPath)
        for k,op in enumerate(objProps):
            cName = 'http://www.ease-crc.org/ont/SOMA_QUERY.owl#QUERY%d' % k
            queryMap[cName] = op
            outfile.write('EquivalentClasses(<%s> ObjectSomeValuesFrom(<%s> owl:Thing))\n' % (cName, op))
        outfile.write(')\n')
    return queryMap, queryOwl

def runKonclude(queryOwl, koncludePath):
    outName = queryOwl[:queryOwl.rfind('.')] + '_OUT.html'
    os.system("%s classification -i %s -o %s >/dev/null 2>&1" % (koncludePath, queryOwl, outName))
    return _parseHomebrew(open(outName).read())

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('konclude_output', help='Konclude output file', type=str)
    parser.add_argument('-k', '--koncludePath', help='path to Konclude executable', default='./.github/actions/Konclude', type=str)
    parser.add_argument('-s', '--somaPath', help='path to merged SOMA owl', default='./build/SOMA-HOME.owl', type=str)
    parser.add_argument('-p', '--propertyListPath', help='object property list file', default='', type=str)
    parser.add_argument('-o', '--output', help='output file', default='konclude.html', type=str)
    args = parser.parse_args()

    nullClasses = _parseHomebrew(open(args.konclude_output).read())
    results = {
        'EquivalentClasses': [nullClasses],
        'SubClassOf': [],
        'EquivalentObjectProperties': [],
        'SubObjectPropertyOf': [],
        'Error': [],
    }

    objProps = []
    if '' != args.propertyListPath:
        objProps = [x.strip() for x in open(args.propertyListPath).read().splitlines() if x.strip()]
    if objProps:
        query2objectProperty, queryOwl = makeObjectPropertyQueryFile(objProps, args.somaPath)
        nullQueries = runKonclude(queryOwl, args.koncludePath)
        for n in nullQueries:
            if n in query2objectProperty:
                results['EquivalentObjectProperties'].append(query2objectProperty[n])
        
    output = format_output(results, format=args.output[args.output.rfind('.'):])
    with open(args.output, 'w') as outfile:
        _ = outfile.write(output)
    print(format_output(results))

    # check if there are problems, return non-zero exit code if so
    if len(nullClasses) != 0:
        sys.exit(1)


def as_html(titles, results):
    template = '<h2>{title}</h2>\n{issues}'
    outer = '<section>\n<p><h3>Set</h3>{}</p>\n</section>\n'
    outeritem = '</p><p><h3>Set</h3>'
    inner = '<ul>\n<li>{}</li>\n</ul>\n'
    inneritem = '</li>\n<li>'
    sections = []
    for k in sorted(results.keys()):
        sets = results[k]
        if (not sets) or (not sets[0]):
            issues = '<p>No issues detected.</p>'
        else:
            inners = list(inner.format(inneritem.join(sorted(s))) for s in sets)
            issues = outer.format(outeritem.join(inners))
        section = template.format(title=titles[k], issues=issues)
        sections.append(section)
    sections = '\n'.join(sections)
    return "<!DOCTYPE html>\n<html>\n\t<head>\n\t\t<title>Konclude results</title>\n\t\t<meta charset='utf-8' />\n\t</head>\n\t<body>\n\t\t<h1>Konclude results</h1>\n\t\t\t" + sections + "\n\t</body>\n</html>"


def as_text(titles, results):
    template = '\n## {title}\n\n{issues}'
    outer = '* {}'
    outeritem = '\n* '
    inner = '- {}\n'
    inneritem = '\n  - '
    sections = []
    for k in sorted(results.keys()):
        sets = results[k]
        if (not sets) or (not sets[0]):
            issues = 'No issues detected.\n'
        else:
            inners = list(inner.format(inneritem.join(sorted(s))) for s in sets)
            issues = outer.format(outeritem.join(inners))
        section = template.format(title=titles[k], issues=issues)
        sections.append(section)
    sections = '\n'.join(sections)
    return '# Konclude results\n\n' + sections


def format_output(results, format='.md'):
    titles = {
        'EquivalentClasses': 'Classes equivalent to ' + OWL_NOTHING,
        'SubClassOf': 'Subclasses of ' + OWL_NOTHING,
        'EquivalentObjectProperties': 'Object properties equivalent to ' + BOTTOM_OBJECT_PROPERTY,
        'SubObjectPropertyOf': 'Subproperties of ' + BOTTOM_OBJECT_PROPERTY,
        'Error': 'Unhandled subsumptions',
    }

    if format == '.html':
        return as_html(titles, results)
    return as_text(titles, results)


if __name__ == '__main__':
    main()

