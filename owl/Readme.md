# Readme
The SOMA ontology has a complex import structure, which is explained in the original paper [10].
However, For the evaluation of the presented paper, we believe two of these to be particularly relevant:

* SOMA-ALL.owl imports most modules and contains all axioms relevant for the submitted paper.
* MOIN-TEST.owl imports SOMA-ALL and contains a simple ABox that we used to test the queries from section 4.

The file catalog-v001.xml is submitted such that tools like Protégé do not search for imported ontologies in the web (which might change during the evaluation period), but from this folder.