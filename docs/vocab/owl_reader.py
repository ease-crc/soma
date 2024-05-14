'''
This file parses the owl ontologies to get the comments, label and superclass of entities.
'''

import owlready2 as owl
import sys
from typing import NamedTuple
from operator import attrgetter


class OWLClassInfo(NamedTuple):
	name: None
	label: None
	superclass: None
	comment: None

class OWLReader:

	def __init__(self, iri_list):
		self.iri_list = iri_list
		self.class_objects = []

	def get_classes(self):
		'''
		Gets the classes defined in the ontology their corresponding
		superclass, label and comment
		'''
		for iri in self.iri_list:
			self._load_ontology(iri)
			self.class_objects = self._create_class_objects() + self.class_objects
		self.class_objects = sorted(self.class_objects, key=attrgetter('name'))
		self._printdict(self.class_objects)
		#return self.class_objects

	def _print(self):
		for obj in self.class_objects:
			print(obj.label, str(obj.superclass), str(obj.comment))

	def _load_ontology(self, iri):
		self.ontology = owl.get_ontology(iri).load()
		self.namespace = self.ontology.get_namespace(iri+"#")
		self.prefix = self._parse_string(iri, "/", -1)[:-3]

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
	    class_obj = getattr(getattr(self.namespace, target_class)
	    print(f"Class_obj {class_obj}")

		comment = getattr(getattr(self.namespace, target_class), "comment")
		if(comment):
			comment = self.split_comment_into_sentences(comment[0])[0]
		label = getattr(
			getattr(getattr(self.namespace, target_class), "label"), "en")
		superclass = self._get_valid_string(
			getattr(getattr(self.namespace, target_class), "is_a")[0])

		return (label, superclass, comment)

	def split_comment_into_sentences(self, text):
		#text = " " + text + "  "
		if not text.endswith("."):
			text = text + "."
		text = text.replace("\n", " ")
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

	def _get_valid_string(self, temp_val):
		if(isinstance(temp_val, list)):
			final_val = []
			for i in temp_val:
				i = str(i)
				final_val.append(self._parse_string(i, ".", 1))
			return final_val
		else:
			return self._parse_string(str(temp_val), ".", 1)

	def _get_string_list(self, object_list, prefix):
		temp = []
		for i in list(object_list):
			if str(i).startswith(prefix):
				temp.append(self._parse_string(str(i), ".", 1))
		return temp

	def _parse_string(self, string_val, split_param, desired_str_pos):
		if split_param in string_val:
			return string_val.split(split_param)[desired_str_pos]
		else:
			return string_val

	def _printdict(self, class_obj):
		for obj in class_obj:
			if (obj.comment):
				print(r'\appendixstyle{{{}}} {{$\sqsubseteq$ }} \textit{{{}}} {{--}} {{{}}}\\'.format(
				    obj.label, str(obj.superclass), str(obj.comment)))
			else:
				print(r'\appendixstyle{{{}}} {{$\sqsubseteq$ }} \textit{{{}}}.\\'.format(
				    obj.label, str(obj.superclass)))