package main.ci_runners;

import main.OntologyManager;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.semanticweb.owlapi.search.EntitySearcher.*;

@Component
@Lazy
public class ElanCVGenerator implements CIRunnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElanCVGenerator.class);

    private final OntologyManager ontologyManager;
    private final String outputPath = "../SOMA.ecv";
    private final String version;
    private final List<OWLOntology> ontologies;
    private final String langRef = "und";
    private final String langLabel = "und";
    private final HashMap<String, List<String>> vocabs;
    private final OWLAnnotationProperty elanNameAnnot;
    private final OWLAnnotationProperty elanUsageAnnot;

    @Autowired
    public ElanCVGenerator(final OntologyManager ontologyManager, @Value("${versionInfo}") final String versionInfo) {
        this.ontologyManager = ontologyManager;
        this.version = versionInfo;

        String[] ontologyStrings = {"SOMA", "SOMA-NEEM", "SOMA-OBJ", "SOMA-PROC", "SOMA-STATE", "SOMA-ACT", "SOMA-ELAN"};
        ontologies = new ArrayList<>();
        for (OWLOntology ontology : ontologyManager.getOntologyManager().getOntologies()) {
            for (String ontologyString : ontologyStrings) {
                if (ontology.getOntologyID().getOntologyIRI().get().toString().contains(ontologyString + ".owl")) {
                    ontologies.add(ontology);
                }
            }
        }

        String ease = "http://www.ease-crc.org/ont/SOMA.owl#";
        vocabs = new HashMap<>();
        vocabs.put("lvl1-ease_tsd-motion", List.of(ease + "Locomotion",
                                                   ease + "BodyMovement",
                                                   ease + "Manipulating",
                                                   ease + "Actuating"));
        vocabs.put("lvl_2-ease_tsd-task_step", List.of(ease + "PhysicalTask"));
        vocabs.put("think-aloud_coding", List.of(ease + "ThinkAloudTopic"));

        elanNameAnnot = getAnnotationByIRI(ease + "ELANName");
        elanUsageAnnot = getAnnotationByIRI(ease + "ELANUsageGuideline");
    }

    @Override
    public void run() {
        if (elanNameAnnot == null) {
            LOGGER.error("Could not find ELANName annotation property. ELAN CV will not be generated.");
            return;
        }

        HashMap<String, List<OWLClass>> vocabWords = new HashMap<>();

        for (String cv_id : vocabs.keySet()) {
            Set<OWLClass> actionWords = new HashSet<>();
            Set<OWLClass> actionParents = new HashSet<>();
            for (String classIRIString : vocabs.get(cv_id)) {
                OWLClass owlClass = getClassByIRI(classIRIString);
                if (owlClass != null) {
                    actionParents.add(owlClass);
                    if (!Objects.equals(getAnnotationValue(owlClass, elanNameAnnot), "")) {
                        actionWords.add(owlClass);
                    }
                } else {
                    LOGGER.error("Could not find class with IRI {}", classIRIString);
                }
            }

            while (!actionParents.isEmpty()) {
                Set<OWLClass> newActionParents = new HashSet<>();
                for (OWLClass actionParent : actionParents) {
                    getSubClasses(actionParent, ontologies.stream()).forEach((classExpression) -> {
                        if (classExpression.isNamed()) {
                            OWLClass owlClass = classExpression.asOWLClass();
                            actionWords.add(owlClass);
                            newActionParents.add(owlClass);
                        }
                    });
                }
                actionParents = newActionParents;
            }
            vocabWords.put(cv_id, actionWords.stream().sorted(Comparator.comparing(this::getElanName)).toList());
        }

        try {
            exportCV(vocabWords);
        } catch (ParserConfigurationException | TransformerException | IOException e) {
            LOGGER.error("Could not export CV", e);
        }
    }

    private OWLClass getClassByIRI(String classIRIString) {
        OWLOntologyManager owlOntologyManager = ontologyManager.getOntologyManager();
        OWLDataFactory dataFactory = owlOntologyManager.getOWLDataFactory();

        // Convert the IRI string to an IRI object
        IRI classIRI = IRI.create(classIRIString);

        // Iterate through the loaded ontologies to find the class
        for (OWLOntology ontology : ontologies) {
            // Check if the ontology contains the entity with the given IRI
            if (ontology.containsEntityInSignature(classIRI)) {
                // Retrieve the entity as an OWLClass
                return dataFactory.getOWLClass(classIRI);
            }
        }

        return null; // Class not found
    }

    private String getElanName(OWLClass owlClass) {
        String elanName = getAnnotationValue(owlClass, elanNameAnnot);
        if (Objects.equals(elanName, "")) {
            elanName = owlClass.getIRI().getShortForm();
        }
        return elanName;
    }

    private String getAnnotationValue(OWLClass owlClass, OWLAnnotationProperty annotationProperty) {
        try {
            return getAnnotations(owlClass, ontologies.stream(), annotationProperty).findAny().get().getValue().asLiteral().get().getLiteral();
        } catch (java.util.NoSuchElementException e) {
            return "";
        }
    }

    private OWLAnnotationProperty getAnnotationByIRI(String annotationIRIString) {
        OWLDataFactory dataFactory = ontologyManager.getOntologyManager().getOWLDataFactory();
        IRI annotationPropertyIRI = IRI.create(annotationIRIString);
        return dataFactory.getOWLAnnotationProperty(annotationPropertyIRI);
    }

    private String getCVEID(String cvId, String elanName) {
        String input = langRef + cvId + elanName;

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] digest = md.digest();

            StringBuilder hexString = new StringBuilder();
            hexString.append("cveid_");
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Could not generate CVE ID for {}", input);
            return "";
        }
    }

    private void exportCV(HashMap<String, List<OWLClass>> vocabWords) throws ParserConfigurationException, TransformerException, IOException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Document doc = docBuilder.newDocument();
        doc.setXmlStandalone(true);
        Element rootElement = doc.createElement("CV_RESOURCE");
        rootElement.setAttribute("AUTHOR", "");
        rootElement.setAttribute("DATE", df.format(Calendar.getInstance().getTime()));
        rootElement.setAttribute("VERSION", version);
        rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        rootElement.setAttribute("xsi:noNamespaceSchemaLocation", "http://www.mpi.nl/tools/elan/EAFv2.8.xsd");
        doc.appendChild(rootElement);

        Element lang = doc.createElement("LANGUAGE");
        lang.setAttribute("LANG_DEF", "http://cdb.iso.org/lg/CDB-00130975-001");
        lang.setAttribute("LANG_ID", langRef);
        lang.setAttribute("LANG_LABEL", langLabel);
        rootElement.appendChild(lang);

        ArrayList<String> cvIdsSorted = new ArrayList<>(vocabWords.keySet());
        cvIdsSorted.sort(String::compareTo);
        for (String cv_id : cvIdsSorted) {
            Element cv = doc.createElement("CONTROLLED_VOCABULARY");
            cv.setAttribute("CV_ID", cv_id);
            rootElement.appendChild(cv);
            Element cvDesc = doc.createElement("DESCRIPTION");
            cvDesc.setAttribute("LANG_REF", langRef);
            cv.appendChild(cvDesc);

            for (OWLClass actionWord : vocabWords.get(cv_id)) {
                String elanName = getElanName(actionWord);
                String elanUsage = getAnnotationValue(actionWord, elanUsageAnnot);
                String cveId = getCVEID(cv_id, elanName);

                Element cvEntry = doc.createElement("CVE_ENTRY_ML");
                cvEntry.setAttribute("CVE_ID", cveId);
                cv.appendChild(cvEntry);
                Element cvEntryVal = doc.createElement("CVE_VALUE");
                cvEntryVal.setTextContent(elanName);
                cvEntryVal.setAttribute("DESCRIPTION", elanUsage);
                cvEntryVal.setAttribute("LANG_REF", langRef);
                cvEntry.appendChild(cvEntryVal);
            }
        }

        try (FileOutputStream output = new FileOutputStream(outputPath)) {
            writeXml(doc, output);
        }
    }

    // write doc to output stream
    private static void writeXml(Document doc, OutputStream output) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(output);

        transformer.transform(source, result);
        LOGGER.info("ECV exported");
    }
}
