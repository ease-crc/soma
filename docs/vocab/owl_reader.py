"""
This file parses the owl ontologies to get the comments, label and superclass of entities.
"""
import owlready2 as owl
import sys
from typing import NamedTuple
from operator import attrgetter


class OWLClassInfo(NamedTuple):
    name: None
    label: None
    superclass: None
    comment: None


def escape_latex(text):
    replacements = {
        '\\': r'\textbackslash{}',
        '#': r'\#',
        '$': r'\$',
        '%': r'\%',
        '&': r'\&',
        '_': r'\_',
        '{': r'\{',
        '}': r'\}',
        '~': r'\textasciitilde{}',
        '^': r'\textasciicircum{}',
    }

    for original, replacement in replacements.items():
        text = text.replace(original, replacement)

    return text


def split_comment_into_sentences(text):
    if not text.endswith("."):
        text += "."

    text = ' '.join(text.splitlines())
    if "e.g." in text:
        text = text.replace("e.g.", "e<prd>g<prd>")
    if "i.e." in text:
        text = text.replace("i.e.", "i<prd>e<prd>")
    if "\"" in text:
        text = text.replace(".\"", "\".")
    if "!" in text:
        text = text.replace("!\"", "\"!")
    if "?" in text:
        text = text.replace("?\"", "\"?")

    text = text.replace(".", ".<stop>")
    text = text.replace("?", "?<stop>")
    text = text.replace("!", "!<stop>")
    text = text.replace("<prd>", ".")

    comments = text.split("<stop>")
    comments = comments[:-1]
    comments = [s.strip() for s in comments]
    return comments


def _parse_string(string_val, split_param, desired_str_pos):
    if split_param in string_val:
        return string_val.split(split_param)[desired_str_pos]
    else:
        return string_val


def _get_string_list(object_list, prefix):
    temp = []
    for i in list(object_list):
        if str(i).startswith(prefix):
            temp.append(_parse_string(str(i), ".", 1))
    return temp


def _printdict(class_obj):
    for obj in class_obj:
        if obj.comment:
            print(r'\appendixstyle{{{}}} {{$\sqsubseteq$ }} \textit{{{}}} {{--}} {{{}}}\\'.format(
                escape_latex(obj.label), escape_latex(str(obj.superclass)), escape_latex(obj.comment)))
        else:
            print(r'\appendixstyle{{{}}} {{$\sqsubseteq$ }} \textit{{{}}}.\\'.format(
                escape_latex(obj.label), escape_latex(str(obj.superclass))))


def _get_valid_string(temp_val):
    if isinstance(temp_val, list):
        final_val = []
        for i in temp_val:
            i = str(i)
            final_val.append(_parse_string(i, ".", 1))
        return final_val
    else:
        return _parse_string(str(temp_val), ".", 1)


class OWLReader:
    def __init__(self, iri_list):
        self.iri_list = iri_list
        self.class_objects = []

    def get_classes(self):
        """
        Gets the classes defined in the ontology their corresponding
        superclass, label and comment
        """
        for iri in self.iri_list:
            self._load_ontology(iri)
            self.class_objects = self._create_class_objects() + self.class_objects
        self.class_objects = sorted(self.class_objects, key=attrgetter('name'))
        _printdict(self.class_objects)

    def _print(self):
        for obj in self.class_objects:
            print(obj.label, str(obj.superclass), str(obj.comment))

    def _load_ontology(self, iri):
        self.ontology = owl.get_ontology(iri).load()
        self.namespace = self.ontology.get_namespace(iri + "#")
        self.prefix = _parse_string(iri, "/", -1)[:-3]

    def _create_class_objects(self):
        objects = []
        for class_name in list(self.ontology.classes()):
            if str(class_name).startswith(self.prefix):
                class_name = str(class_name).split(".")[1]
                class_info = self.set_class_info(class_name)
                objects.append(OWLClassInfo(name=class_name,
                                            label=class_info[0][0] if class_info[0] else class_name,
                                            superclass=class_info[1],
                                            comment=class_info[2]))
        return objects

    def set_class_info(self, target_class):
        try:
            target_class_instance = getattr(self.namespace, target_class)
            comment = getattr(target_class_instance, "comment")
        except AttributeError as e:
            raise AttributeError(
                "Error accessing 'comment' attribute. The target class '{}' might be incorrect or missing.".format(
                    target_class)
            ) from e
        if comment:
            comment = split_comment_into_sentences(comment[0])[0]
        label = getattr(
            getattr(target_class_instance, "label"), "en")
        superclass = _get_valid_string(
            getattr(target_class_instance, "is_a")[0])

        return label, superclass, comment
