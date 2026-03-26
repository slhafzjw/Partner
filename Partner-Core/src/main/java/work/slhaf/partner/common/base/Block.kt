package work.slhaf.partner.common.base

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

abstract class Block(
    val blockName: String
) {

    fun encodeToXml(): Element {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .newDocument()

        val root = document.createElement(blockName)
        document.appendChild(root)
        appendRootAttributes().forEach { attribute, value -> root.setAttribute(attribute, value) }
        fillXml(document, root)

        return root
    }

    protected open fun appendRootAttributes(): Map<String, String> {
        return emptyMap()
    }

    fun encodeToXmlString(): String {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }

        return StringWriter().use { writer ->
            transformer.transform(DOMSource(encodeToXml()), StreamResult(writer))
            writer.toString()
        }
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

    @JvmOverloads
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

    @JvmOverloads
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
}