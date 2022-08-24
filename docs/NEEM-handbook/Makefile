BASE_NAME=main
BASE_DIR:=$(dir $(firstword $(MAKEFILE_LIST)))

DELETE_EXTENSIONS=log fdb_latexmk aux bbl blg toc out fls thm
DELETE_FILES=${foreach extension,$(DELETE_EXTENSIONS),${BASE_DIR}${BASE_NAME}.${extension}}

${BASE_DIR}${BASE_NAME}.pdf:
	cd ${BASE_DIR} && latexmk --pdf ${BASE_NAME}.tex
	rm -f ${DELETE_FILES}

clean:
	rm -f ${DELETE_FILES}
