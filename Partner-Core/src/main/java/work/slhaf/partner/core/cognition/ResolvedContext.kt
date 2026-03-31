package work.slhaf.partner.core.cognition

import org.w3c.dom.Document
import work.slhaf.partner.api.agent.model.pojo.Message
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class ResolvedContext(
    val blocks: List<BlockContent>
) {

    fun encodeToMessage(): Message {
        val content = if (blocks.isEmpty()) {
            "<no_context></no_context>"
        } else {
            buildContextXml(blocks)
        }
        return Message(Message.Character.USER, content)
    }

    private fun buildContextXml(blocks: List<BlockContent>): String {
        return try {
            val document = newDocument()
            val root = document.createElement("context")
            document.appendChild(root)

            blocks.stream()
                .map(BlockContent::encodeToXml)
                .forEach { blockElement ->
                    root.appendChild(document.importNode(blockElement, true))
                }

            toXmlString(document)
        } catch (e: Exception) {
            throw IllegalStateException("构建 context 区段失败", e)
        }
    }

    private fun newDocument(): Document {
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .newDocument()
    }

    private fun toXmlString(document: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }
        return StringWriter().use { writer ->
            transformer.transform(DOMSource(document), StreamResult(writer))
            writer.toString()
        }
    }
}
