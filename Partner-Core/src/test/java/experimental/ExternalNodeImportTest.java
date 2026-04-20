package experimental;

import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalNodeImportTest {

    private static Node importExternalNode(Document targetDocument, Node externalNode) {
        Node nodeToImport = externalNode;
        if (externalNode != null && externalNode.getNodeType() == Node.DOCUMENT_NODE) {
            nodeToImport = ((Document) externalNode).getDocumentElement();
        }
        if (nodeToImport == null) {
            throw new IllegalArgumentException("nodeToImport must not be null");
        }
        if (nodeToImport.getOwnerDocument() == targetDocument) {
            return nodeToImport.cloneNode(true);
        }
        return targetDocument.importNode(nodeToImport, true);
    }

    private static Document newDocument() throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .newDocument();
    }

    @Test
    void appendForeignElementDirectlyShouldThrow() throws Exception {
        Document targetDocument = newDocument();
        Element inputRoot = targetDocument.createElement("input");
        targetDocument.appendChild(inputRoot);

        Document externalDocument = newDocument();
        Element externalInputs = externalDocument.createElement("inputs");
        externalDocument.appendChild(externalInputs);

        assertThrows(DOMException.class, () -> inputRoot.appendChild(externalInputs));
    }

    @Test
    void importForeignElementShouldWork() throws Exception {
        Document targetDocument = newDocument();
        Element inputRoot = targetDocument.createElement("input");
        targetDocument.appendChild(inputRoot);

        Document externalDocument = newDocument();
        Element externalInputs = externalDocument.createElement("inputs");
        externalDocument.appendChild(externalInputs);

        Node imported = importExternalNode(targetDocument, externalInputs);
        inputRoot.appendChild(imported);

        assertEquals("inputs", inputRoot.getFirstChild().getNodeName());
    }

    @Test
    void importExternalDocumentShouldUseDocumentElement() throws Exception {
        Document targetDocument = newDocument();
        Element inputRoot = targetDocument.createElement("input");
        targetDocument.appendChild(inputRoot);

        Document externalDocument = newDocument();
        Element externalInputs = externalDocument.createElement("inputs");
        externalDocument.appendChild(externalInputs);

        Node imported = importExternalNode(targetDocument, externalDocument);
        inputRoot.appendChild(imported);

        assertEquals("inputs", inputRoot.getFirstChild().getNodeName());
    }
}
