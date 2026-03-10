package work.slhaf.partner.api.agent.runtime.interaction.flow

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

abstract class ContextBlock {

    abstract val priority: Int
    abstract val type: Type

    abstract val blockName: String
    abstract val source: String

    enum class Type {
        CONTEXT,
        SUPPLY
    }

    fun encodeToXml(): String {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .newDocument()

        val root = document.createElement(blockName)
        root.setAttribute("source",source)
        document.appendChild(root)

        fillXml(document, root)

        return document.toXmlString()
    }

    protected abstract fun fillXml(document: Document, root: Element)

    protected fun appendTextElement(
        document: Document,
        parent: Element,
        tagName: String,
        value: Any?
    ): Element {
        val element = document.createElement(tagName)
        element.textContent = value?.toString() ?: ""
        parent.appendChild(element)
        return element
    }

    protected fun appendChildElement(
        document: Document,
        parent: Element,
        tagName: String,
        block: Element.() -> Unit = {}
    ): Element {
        val element = document.createElement(tagName)
        parent.appendChild(element)
        element.block()
        return element
    }

    protected fun appendCDataElement(
        document: Document,
        parent: Element,
        tagName: String,
        value: String?
    ): Element {
        val element = document.createElement(tagName)
        element.appendChild(document.createCDATASection(value ?: ""))
        parent.appendChild(element)
        return element
    }

    protected fun <T> appendListElement(
        document: Document,
        parent: Element,
        wrapperTagName: String,
        itemTagName: String,
        values: Iterable<T>,
        block: Element.(T) -> Unit = { value ->
            textContent = value?.toString() ?: ""
        }
    ): Element {
        val wrapper = document.createElement(wrapperTagName)
        parent.appendChild(wrapper)

        for (value in values) {
            val item = document.createElement(itemTagName)
            wrapper.appendChild(item)
            item.block(value)
        }

        return wrapper
    }

    protected fun <T> appendRepeatedElements(
        document: Document,
        parent: Element,
        itemTagName: String,
        values: Iterable<T>,
        block: Element.(T) -> Unit = { value ->
            textContent = value?.toString() ?: ""
        }
    ) {
        for (value in values) {
            val item = document.createElement(itemTagName)
            parent.appendChild(item)
            item.block(value)
        }
    }

    private fun Document.toXmlString(): String {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }

        return StringWriter().use { writer ->
            transformer.transform(DOMSource(this), StreamResult(writer))
            writer.toString()
        }
    }
}
