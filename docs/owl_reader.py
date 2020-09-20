'''
This file parses the owl ontologies to get the comments, label and superclass of entities.
'''

import owlready2 as owl
import time
import sys
from collections import OrderedDict
import re

class OWLReader:

	def __init__(self, iri_list):
		self.props = ["superclass", "label", "comment", "Defined in"]
		self.iri_list = iri_list
		if len(iri_list) == 1:
			self.props = self.props[0:3]

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

	def get_classes(self):
		''' 
		Gets the classes defined in the ontology their corresponding
		superclass, label and comment
		'''
		class_dict = {}
		for iri in self.iri_list:
			class_dict.update(self._get_values(iri))
			ordered_dict = OrderedDict(sorted(class_dict.items()))
		self._printdict(ordered_dict)
	
	def _get_values(self, iri):
		ontology = owl.get_ontology(iri).load()
		base_iri = iri+"#"
		namespace = ontology.get_namespace(base_iri)
		prefix = self._parse_string(iri, "/", -1)[:-3]
		classes = self._get_string_list(ontology.classes(), prefix)
		class_dict = {}
		for i in classes:
			temp_dict = {}
			for prop in self.props:
				a = self._get_class_relation(namespace,i,prop)
				if not a and prop == "Defined in":
					temp_dict[prop] = prefix
				elif not a:
					continue
				else:
					temp_dict[prop] = a
			class_dict[i] = temp_dict
		return class_dict

	def _get_valid_string(self, temp_val):
		if(isinstance(temp_val, list)):
			final_val = []
			for i in temp_val:
				i = str(i)
				final_val.append(self._parse_string(i, ".", 1))
			return final_val
		else:
			return self._parse_string(str(temp_val), ".", 1)

	def _get_class_relation(self, namespace, sub, rel):
		rel = rel.lower()
		try:
			if(rel == "superclass"):
				return self._get_valid_string(getattr(getattr(namespace, sub),"is_a")[0])
			elif(rel == "defined in"):
				return self._get_valid_string(getattr(getattr(namespace, sub),"isDefinedBy"))
			elif(rel == "comment"):
				value_str = getattr(getattr(namespace, sub),rel)
				if(value_str):
					value_str = value_str[0].split('.')[0] + '.'
					pattern = "[.!?][\s]{1,2}(?=[A-Z].)"
					return re.split(pattern,value_str)[0]
			return getattr(getattr(namespace, sub),rel)
		except AttributeError:
			print("Please pass the right parameters." f"{sub}" " does not have " f"{rel}")
			return

	def _printdict(self, class_dict):
		for key,val in class_dict.items() :
			print(r'\appendixstyle', end="")
			print(r'{{{}}}'.format(key),end=" ")
			print(r'{', end="")
			for j,k in val.items():
				#print(r'\textit{{{}}}'.format(j),end=" ")
				#print(r"\hspace*{0.0008cm}")
				print(r'{-- }', end="")
				#print(r"\hspace*{0.0008cm}")
				if isinstance(k,list):
					print(', '.join(k),end=" ")
					#print(r"\hspace*{0.0008cm}")
				else:
					print(k,end=" ")
					#print(r"\hspace*{0.0008cm}")
			print(r'}')
			print(r"\linebreak")
		
