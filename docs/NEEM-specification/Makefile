OUTPUT_FILE=neems.pdf
BASE_NAME=main
DELETE_EXTENSIONS=log fdb_latexmk aux bbl blg toc out fls thm
DELETE_FILES=${foreach extension,$(DELETE_EXTENSIONS),${BASE_NAME}.${extension}}

${OUTPUT_FILE}:
	latexmk --pdf ${BASE_NAME}.tex
	mv ${BASE_NAME}.pdf ${OUTPUT_FILE}
	rm -f ${DELETE_FILES}

clean:
	rm -f ${DELETE_FILES}
	-rm ${OUTPUT_FILE}
