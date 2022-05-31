#!/usr/bin/env python
import argparse
import re
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

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('konclude_output', help='Konclude output file', type=str)
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
        if not sets:
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
        if not sets:
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

