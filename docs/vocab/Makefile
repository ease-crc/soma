BASE_NAME=SOMA-vocabulary
BASE_DIR:=$(dir $(firstword $(MAKEFILE_LIST)))
PYTHONTEX_DIR=pythontex-files-

DELETE_EXTENSIONS=log fdb_latexmk aux bbl blg toc out fls thm pytxcode
DELETE_FILES=${foreach extension,$(DELETE_EXTENSIONS),${BASE_DIR}${BASE_NAME}.${extension}}
#DELETE_FOLDER=${BASE_DIR}/${PYTHONTEX_DIR}${BASE_NAME}

rwildcard=$(foreach d,$(wildcard $(1:=/*)),$(call rwildcard,$d,$2) $(filter $(subst *,%,$2),$d))

${BASE_DIR}${BASE_NAME}.pdf: $(call rwildcard,.,*.tex *.bib)
	cd ${BASE_DIR}
	pdflatex -interaction=nonstopmode ${BASE_NAME}.tex
	#bibtex ${BASE_NAME}
	#pythontex3 ${BASE_NAME}.tex
	#pdflatex -interaction=nonstopmode ${BASE_NAME}.tex
	rm -f ${DELETE_FILES}
	#rm -rf ${DELETE_FOLDER}

clean:
	rm -f ${DELETE_FILES}
	#rm -rf ${DELETE_FOLDER}
	rm -f ${BASE_NAME}.pdf

