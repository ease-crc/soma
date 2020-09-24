BASE_NAME=SOMA-vocabulary

DELETE_EXTENSIONS=pdf log fdb_latexmk aux bbl blg toc out nav snm vrb fls synctex.gz

TARGET_FILES=${foreach base_name,${BASE_NAMES},${base_name}.tex}
DELETE_FILES=${foreach extension,$(DELETE_EXTENSIONS),${foreach base_name,${BASE_NAMES}, ${base_name}.${extension}}}

all: pdf

pdf:
	latexmk --pdf SOMA-vocabulary.tex

clean:
	rm -f ${DELETE_FILES}

