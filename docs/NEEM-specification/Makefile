BASE_NAME=main
BASE_DIR:=$(dir $(firstword $(MAKEFILE_LIST)))

DELETE_EXTENSIONS=log fdb_latexmk aux bbl blg toc out fls thm
DELETE_FILES=${foreach extension,$(DELETE_EXTENSIONS),${BASE_DIR}${BASE_NAME}.${extension}}

rwildcard=$(foreach d,$(wildcard $(1:=/*)),$(call rwildcard,$d,$2) $(filter $(subst *,%,$2),$d))

${BASE_DIR}${BASE_NAME}.pdf: $(call rwildcard,.,*.tex *.bib)
	cd ${BASE_DIR} && latexmk --pdf ${BASE_NAME}.tex
	rm -f ${DELETE_FILES}

clean:
	rm -f ${DELETE_FILES}
