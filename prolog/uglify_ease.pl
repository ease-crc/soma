
:- module(uglify_ease,
    [
      uglify_ease/0
    ]).

:- use_module(library('semweb/rdfs')).
:- use_module(library('semweb/rdf_db')).

ease_ugly_ontology('http://www.ease-crc.org/ont/EASE-UGLY.owl').

ease_assert(_Graph, rdf(_S,_P,O)) :-
  rdf_equal(O,owl:'Ontology'),!.

ease_assert(Graph, rdf(_S,P,O)) :-
  rdf_equal(P,owl:imports),!,
  ( rdf_split_url('http://www.ease-crc.org/ont/',_,O) -> true ; (
    ease_ugly_ontology(EASE_UGLY),
    rdf_assert(EASE_UGLY,P,O,Graph)
  )).

ease_assert(Graph, rdf(S,P,O)) :-
  rdf_assert(S,P,O,Graph).

ease_load(URL, Graph) :-
  load_rdf(URL, Triples, [blank_nodes(noshare)]),
  maplist(ease_assert(Graph), Triples).

uglify_ease :-
  ease_ugly_ontology(EASE_UGLY),
  Ontologies=[
    'EASE.owl',
    'EASE-ACT.owl',
    'EASE-WF.owl',
    'EASE-IO.owl',
    'EASE-OBJ.owl'
  ],
  rdf_assert(EASE_UGLY,rdf:type,owl:'Ontology',ease),
  source_file(uglify_ease, Filepath),
  string_concat(Basepath, '/prolog/uglify_ease.pl', Filepath),
  forall(member(N,Ontologies), (
      atomic_list_concat([Basepath, '/owl/', N], GlobalPath),
      ease_load(GlobalPath,ease)
  )),
  %%%
  atomic_list_concat([Basepath, '/owl/EASE-UGLY.owl'], OUT_Path),
  rdf_save(OUT_Path, [graph(ease),sorted(true)]).
