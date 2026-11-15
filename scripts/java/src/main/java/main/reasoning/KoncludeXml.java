package main.reasoning;

import org.w3c.dom.Element;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import org.xml.sax.InputSource;
import java.util.*;

/** Parse the native OWL/XML taxonomy structurally; never infer success from missing output. */
public final class KoncludeXml {
    private KoncludeXml() {}
    private static final String OWL = "http://www.w3.org/2002/07/owl#";
    public static Set<String> bottomClasses(String xml, Set<String> expected) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var root = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
        require(root, "Ontology");
        var declared = new HashSet<String>();
        var edges = new HashMap<String, Set<String>>();
        for (var axiom : children(root)) {
            if (!OWL.equals(axiom.getNamespaceURI())) throw new IllegalArgumentException("Foreign taxonomy element");
            String type = axiom.getLocalName();
            if (type.equals("Prefix")) continue;
            var values = children(axiom).stream().map(KoncludeXml::classIri).toList();
            if (type.equals("Declaration") && values.size() == 1) declared.add(values.getFirst());
            else if (type.equals("EquivalentClasses") && values.size() >= 2) {
                for (String a : values) for (String b : values) edges.computeIfAbsent(a, k -> new HashSet<>()).add(b);
            } else if (type.equals("SubClassOf") && values.size() == 2)
                edges.computeIfAbsent(values.getFirst(), k -> new HashSet<>()).add(values.getLast());
            else throw new IllegalArgumentException("Unexpected taxonomy axiom: " + type);
        }
        var required = new HashSet<>(expected);
        required.add(OWL + "Nothing");
        required.add(OWL + "Thing");
        if (!declared.containsAll(required)) throw new IllegalArgumentException("Incomplete taxonomy signature");
        var bottom = new TreeSet<String>();
        for (String iri : required) {
            var reachable = new HashSet<String>();
            var pending = new ArrayDeque<String>();
            pending.add(iri);
            while (!pending.isEmpty()) {
                String next = pending.removeFirst();
                if (reachable.add(next)) pending.addAll(edges.getOrDefault(next, Set.of()));
            }
            if (reachable.contains(OWL + "Nothing")) bottom.add(iri);
            else if (!reachable.contains(OWL + "Thing")) throw new IllegalArgumentException("Incomplete taxonomy for " + iri);
        }
        if (bottom.contains(OWL + "Thing")) throw new IllegalArgumentException("Classification contradicts consistency");
        bottom.remove(OWL + "Nothing");
        return bottom;
    }

    private static List<Element> children(Element parent) {
        var result = new ArrayList<Element>();
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element element) result.add(element);
            else if (node.getNodeType() == org.w3c.dom.Node.TEXT_NODE && !node.getTextContent().isBlank())
                throw new IllegalArgumentException("Unexpected XML text");
        return result;
    }

    private static String classIri(Element element) {
        require(element, "Class");
        String iri = element.getAttribute("IRI");
        if (!java.net.URI.create(iri).isAbsolute() || !children(element).isEmpty())
            throw new IllegalArgumentException("Expected absolute named class IRI");
        return iri;
    }
    private static void require(Element element, String name) {
        if (!OWL.equals(element.getNamespaceURI()) || !name.equals(element.getLocalName()))
            throw new IllegalArgumentException("Expected OWL/XML " + name);
    }
}
