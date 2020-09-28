BASE_NAME=SOMA-vocabulary
PYTHONTEX_DIR=pythontex-files-

BASE_DIR="${ROOT_DIR}/docs/vocab"

DELETE_EXTENSIONS=log fdb_latexmk aux bbl blg toc out fls thm pytxcode
DELETE_FILES=${foreach extension,$(DELETE_EXTENSIONS),${BASE_DIR}/${BASE_NAME}.${extension}}
DELETE_FOLDER=${BASE_DIR}/${PYTHONTEX_DIR}${BASE_NAME}


rwildcard=$(foreach d,$(wildcard $(1:=/*)),$(call rwildcard,$d,$2) $(filter $(subst *,%,$2),$d))

${BASE_DIR}/${BASE_NAME}.pdf: $(call rwildcard,.,*.tex)
	pdflatex -interaction=nonstopmode ${BASE_DIR}/${BASE_NAME}.tex
	pythontex ${BASE_DIR}/${BASE_NAME}.tex
	pdflatex -interaction=nonstopmode ${BASE_DIR}/${BASE_NAME}.tex
	rm -f ${BASE_DIR}/${DELETE_FILES}
	rm -rf ${DELETE_FOLDER}

clean:
	rm -f ${BASE_DIR}/${DELETE_FILES}
	rm -rf ${BASE_DIR}/${DELETE_FOLDER}
	rm -f ${BASE_NAME}.pdf
