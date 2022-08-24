#!/usr/bin/env python
import argparse
import re
import sys
from pathlib import Path


OWL_NOTHING = 'http://www.w3.org/2002/07/owl#Nothing'
BOTTOM_OBJECT_PROPERTY = 'http://www.w3.org/2002/07/owl#bottomObjectProperty'

IRI = '<(.*?)>'

EQUIVALENT_CLASSES = r'^EquivalentClasses\( (.*) \)$'
OBJECT_INVERSE_OF = r'ObjectInverseOf\( ' + IRI + r' \)'
EQUIVALENT_OBJECT_PROPERTIES = r'^EquivalentObjectProperties\( (.*) \)$'


def equivalent_to_nothing(subsumption):
    """Searches for all classes which are equivalent to owl#Nothing"""
    results = re.search(EQUIVALENT_CLASSES, subsumption).group(1)
    classes = set(re.findall(IRI, results))
    return OWL_NOTHING in classes, classes


def subclass_of_nothing(subsumption):
    """Searches for all classes which are subclasses of owl#Nothing"""
    return OWL_NOTHING in subsumption, set(re.findall(IRI, subsumption)[0])


def equivalent_to_bottom_object_property(subsumption):
    """Searches for all ObjectProperties which are equivalent to owl#bottomObjectProperty"""
    results = re.search(EQUIVALENT_OBJECT_PROPERTIES, subsumption).group(1)
    inverse_of = set(re.findall(OBJECT_INVERSE_OF, results))
    all_classes = set(re.findall(IRI, results))

    output = all_classes - inverse_of | set(f'ObjectInverseOf({c})' for c in inverse_of)

    if BOTTOM_OBJECT_PROPERTY in all_classes:
        return True, output
    return False, output


def subobjectproperty_of_bottom_object_property(subsumption):
    """Searches for all ObjectProperties which are SubProperties of owl#bottomObjectProperty"""
    return BOTTOM_OBJECT_PROPERTY in subsumption, set(re.findall(IRI, subsumption)[0])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('hermit_output', help='HermiT output file', type=Path)
    parser.add_argument('-o', '--output', help='output file', default='hermit.html', type=Path)
    args = parser.parse_args()

    subsumptions = [s.strip() for s in args.hermit_output.read_text().splitlines() if s]

    handlers = {
        'EquivalentClasses': equivalent_to_nothing,
        'SubClassOf': subclass_of_nothing,
        'EquivalentObjectProperties': equivalent_to_bottom_object_property,
        'SubObjectPropertyOf': subobjectproperty_of_bottom_object_property,
    }

    results = {k: [] for k in handlers}
    results['Error'] = []
    for subsumption in subsumptions:
        subsumption_name = subsumption.split('(')[0]
        try:
            handler = handlers[subsumption_name]
        except KeyError:
            results['Error'].append({subsumption})
            continue
        result, classes = handler(subsumption)
        if result:
            results[subsumption_name].append(classes)

    output = format_output(results, format=args.output.suffix)
    args.output.write_text(output)
    print(format_output(results))

    # check if there are problems, return non-zero exit code if so
    for k, v in results.items():
        if len(v) != 0:
            sys.exit(1)


def as_html(titles, results):
    template = '<h2>{title}</h2>\n{issues}'
    outer = '<section>\n<p><h3>Set</h3>{}</p>\n</section>\n'
    outeritem = '</p><p><h3>Set</h3>'
    inner = '<ul>\n<li>{}</li>\n</ul>\n'
    inneritem = '</li>\n<li>'
    sections = []
    for k, sets in results.items():
        if not sets:
            issues = '<p>No issues detected.</p>'
        else:
            inners = list(inner.format(inneritem.join(sorted(s))) for s in sets)
            issues = outer.format(outeritem.join(inners))
        section = template.format(title=titles[k], issues=issues)
        sections.append(section)
    sections = '\n'.join(sections)
    return f"""\
    <!DOCTYPE html>
    <html>
        <head>
            <title>HermiT results</title>
            <meta charset='utf-8' />
        </head>
        <body>
            <h1>HermiT result</h1>
            {sections}
        </body>
    </html>"""


def as_text(titles, results):
    template = '\n## {title}\n\n{issues}'
    outer = '* {}'
    outeritem = '\n* '
    inner = '- {}\n'
    inneritem = '\n  - '
    sections = []
    for k, sets in results.items():
        if not sets:
            issues = 'No issues detected.\n'
        else:
            inners = list(inner.format(inneritem.join(sorted(s))) for s in sets)
            issues = outer.format(outeritem.join(inners))
        section = template.format(title=titles[k], issues=issues)
        sections.append(section)
    sections = '\n'.join(sections)
    return f'# HermiT results\n\n{sections}'


def format_output(results, format='.md'):
    titles = {
        'EquivalentClasses': f'Classes equivalent to {OWL_NOTHING}',
        'SubClassOf': f'Subclasses of {OWL_NOTHING}',
        'EquivalentObjectProperties': f'Object properties equivalent to {BOTTOM_OBJECT_PROPERTY}',
        'SubObjectPropertyOf': f'Subproperties of {BOTTOM_OBJECT_PROPERTY}',
        'Error': 'Unhandled subsumptions',
    }

    if format == '.html':
        return as_html(titles, results)
    return as_text(titles, results)


if __name__ == '__main__':
    main()
