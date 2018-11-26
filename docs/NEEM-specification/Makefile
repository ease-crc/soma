BASE_NAME=main
DELETE_EXTENSIONS=pdf log fdb_latexmk aux bbl blg toc out fls
DELETE_FILES=${foreach extension,$(DELETE_EXTENSIONS),${BASE_NAME}.${extension}}

all: pdf

pdf:
	latexmk --pdf ${BASE_NAME}.tex

clean:
	rm -f ${DELETE_FILES}
