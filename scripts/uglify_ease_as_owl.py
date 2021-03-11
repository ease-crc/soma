import subprocess
import sys
from pathlib import Path

import owlready2


def convert():
    owl_file_names = ['SOMA.owl', 'SOMA-HOME.owl']
    for owl_file_name in owl_file_names:
        ease_ugly = Path(__file__).parent.parent / 'build' / owl_file_name
        if not ease_ugly.exists():
            print('Generating rdf')
            command = 'swipl -f prolog/uglify.pl -t uglify'.split()
            proc = subprocess.run(command, cwd='data/ontologies/soma_ontology', capture_output=True)
            print(proc.stdout.decode('utf8'), end='')
            print(proc.stderr.decode('utf8'), end='', file=sys.stderr)
        
        print('Converting to owl')
        owlready2.onto_path.insert(0, str(ease_ugly.parent))
        ontology = owlready2.get_ontology(owl_file_name).load()
        ontology.save()
        print('Converted')


if __name__ == '__main__':
    convert()
