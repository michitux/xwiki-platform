/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.rendering.internal.macro.jexl;

import java.io.StringReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.JexlScript;
import org.jsoup.nodes.Attribute;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.internal.parser.XDOMGeneratorListener;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.ListType;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.MacroPreparationException;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.macro.jexl.JexlEngineManager;
import org.xwiki.rendering.macro.script.AbstractScriptMacro;
import org.xwiki.rendering.macro.script.ScriptMacroParameters;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.util.ParserUtils;
import org.xwiki.script.ScriptContextManager;

/**
 * Executes an XML-based JEXL template and produces an XDOM.
 *
 * @version $Id$
 * @since 18.4.0
 */
@Component
@Named("jexltemplate")
@Singleton
public class JexlTemplateMacro extends AbstractScriptMacro<ScriptMacroParameters>
{
    static final String MACRO_ATTRIBUTE = "jexl.template";

    static final String TEMPLATE_CONTEXT_NAME = "template";

    private static final String DESCRIPTION = "Executes an XML-based JEXL template and produces an XDOM.";

    private static final String CONTENT_DESCRIPTION = "the XML-based JEXL template to execute";

    private static final Syntax RAW_SYNTAX = Syntax.HTML_5_0;

    private static final Set<String> VOID_ELEMENTS = Set.of(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source",
        "track", "wbr");

    private static final Set<String> URL_SCHEMES = Set.of("http", "https", "ftp");

    private static final Pattern FOR_PATTERN = Pattern.compile(
        "^\\s*(?:\\(([^)]+)\\)|([A-Za-z_$][\\w$]*))\\s+in\\s+(.+?)\\s*$");

    @Inject
    private JexlEngineManager jexlEngineManager;

    @Inject
    private ScriptContextManager scriptContextManager;

    private final ParserUtils parserUtils = new ParserUtils();

    /**
     * Default constructor.
     */
    public JexlTemplateMacro()
    {
        super("jexltemplate", DESCRIPTION, new DefaultContentDescriptor(CONTENT_DESCRIPTION),
            ScriptMacroParameters.class);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return true;
    }

    @Override
    protected List<Block> evaluateBlock(ScriptMacroParameters parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        PreparedTemplate template = getTemplate(content, context);

        ScriptContext scriptContext = this.scriptContextManager.getScriptContext();
        if (scriptContext == null) {
            scriptContext = new SimpleScriptContext();
        }

        OverlayJexlContext rootContext = new OverlayJexlContext(this.jexlEngineManager.createContext(scriptContext));
        Map<String, Object> templateValues = new LinkedHashMap<>();
        rootContext.set(TEMPLATE_CONTEXT_NAME, templateValues);

        executeScripts(template, rootContext, templateValues);

        XDOMGeneratorListener listener = new XDOMGeneratorListener();
        listener.beginDocument(MetaData.EMPTY);
        renderNodes(template.bodyNodes, rootContext, listener, template.fragments);
        listener.endDocument(MetaData.EMPTY);

        List<Block> result = new ArrayList<>(listener.getXDOM().getChildren());
        if (context.isInline()) {
            this.parserUtils.convertToInline(result);
        }
        return result;
    }

    @Override
    public void prepare(MacroBlock macroBlock) throws MacroPreparationException
    {
        macroBlock.setAttribute(MACRO_ATTRIBUTE, parseTemplate(macroBlock.getContent()));
    }

    private PreparedTemplate getTemplate(String content, MacroTransformationContext context) throws MacroExecutionException
    {
        MacroBlock currentMacroBlock = context.getCurrentMacroBlock();
        if (currentMacroBlock != null) {
            Object preparedTemplate = currentMacroBlock.getAttribute(MACRO_ATTRIBUTE);
            if (preparedTemplate instanceof PreparedTemplate template) {
                return template;
            }
        }

        try {
            return parseTemplate(content);
        } catch (MacroPreparationException e) {
            throw new MacroExecutionException("Failed to prepare the JEXL template", e);
        }
    }

    private PreparedTemplate parseTemplate(String content) throws MacroPreparationException
    {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document document = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader("<root>" + content + "</root>")));

            List<TemplateNode> bodyNodes = new ArrayList<>();
            List<ScriptNode> scripts = new ArrayList<>();
            Map<String, FragmentNode> fragments = new LinkedHashMap<>();
            parseTopLevelNodes(document.getDocumentElement().getChildNodes(), bodyNodes, scripts, fragments);
            return new PreparedTemplate(bodyNodes, scripts, fragments);
        } catch (ParserConfigurationException | SAXException | JexlException e) {
            throw new MacroPreparationException("Failed to parse the JEXL template", e);
        } catch (Exception e) {
            throw new MacroPreparationException("Failed to parse the JEXL template", e);
        }
    }

    private void parseTopLevelNodes(NodeList childNodes, List<TemplateNode> bodyNodes, List<ScriptNode> scripts,
        Map<String, FragmentNode> fragments) throws MacroPreparationException
    {
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String tagName = element.getTagName();
                if ("script".equals(tagName)) {
                    scripts.add(new ScriptNode(this.jexlEngineManager.createScript(element.getTextContent())));
                    continue;
                }
                if ("v-fragment".equals(tagName)) {
                    String name = element.getAttribute("name");
                    if (name == null || name.isBlank()) {
                        throw new MacroPreparationException("The v-fragment tag requires a name attribute.");
                    }
                    fragments.put(name, new FragmentNode(parseChildNodes(element.getChildNodes())));
                    continue;
                }
            }

            TemplateNode parsedNode = parseNode(node);
            if (parsedNode != null) {
                bodyNodes.add(parsedNode);
            }
        }
    }

    private List<TemplateNode> parseChildNodes(NodeList childNodes) throws MacroPreparationException
    {
        List<TemplateNode> result = new ArrayList<>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            TemplateNode parsedNode = parseNode(childNodes.item(i));
            if (parsedNode != null) {
                result.add(parsedNode);
            }
        }
        return result;
    }

    private TemplateNode parseNode(Node node) throws MacroPreparationException
    {
        return switch (node.getNodeType()) {
            case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> parseTextNode(node.getTextContent());
            case Node.ELEMENT_NODE -> parseElement((Element) node);
            default -> null;
        };
    }

    private TemplateNode parseTextNode(String content)
    {
        if (content == null || content.isEmpty()) {
            return null;
        }
        if (content.isBlank()) {
            return content.contains("\n") || content.contains("\r") ? null : new TextNode(" ");
        }
        return new TextNode(content);
    }

    private TemplateNode parseElement(Element element) throws MacroPreparationException
    {
        ElementDefinition definition = parseElementDefinition(element);
        String tagName = definition.tagName;

        if ("v-set".equals(tagName)) {
            return new SetNode(definition.common, normalizeVariableName(element.getAttribute("var")),
                createExpression(element.getAttribute("value")));
        }
        if ("v-render".equals(tagName)) {
            String fragmentName = element.getAttribute("fragment");
            if (fragmentName == null || fragmentName.isBlank()) {
                throw new MacroPreparationException("The v-render tag requires a fragment attribute.");
            }
            return new RenderFragmentNode(definition.common, fragmentName, definition.boundAttributes);
        }
        if ("xw-img".equals(tagName)) {
            return new ImageNode(definition.common, definition.literalAttributes, definition.boundAttributes);
        }
        if (tagName.startsWith("xw-macro-")) {
            List<TemplateNode> children = parseChildNodes(element.getChildNodes());
            for (TemplateNode child : children) {
                if (!(child instanceof TextNode)) {
                    throw new MacroPreparationException("Nested elements are not supported inside xw-macro-* tags.");
                }
            }
            return new MacroNode(definition.common, tagName.substring("xw-macro-".length()), definition.literalAttributes,
                definition.boundAttributes, children);
        }

        return new ElementNode(definition.common, tagName, definition.literalAttributes, definition.boundAttributes,
            parseChildNodes(element.getChildNodes()));
    }

    private ElementDefinition parseElementDefinition(Element element) throws MacroPreparationException
    {
        String tagName = element.getTagName();
        JexlExpression ifExpression = null;
        ForEachDefinition forEach = null;
        JexlExpression textExpression = null;
        JexlExpression htmlExpression = null;
        Map<String, String> literalAttributes = new LinkedHashMap<>();
        Map<String, JexlExpression> boundAttributes = new LinkedHashMap<>();

        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Node attributeNode = element.getAttributes().item(i);
            String attributeName = attributeNode.getNodeName();
            String attributeValue = attributeNode.getNodeValue();
            if ("v-if".equals(attributeName)) {
                ifExpression = createExpression(attributeValue);
            } else if ("v-for".equals(attributeName)) {
                forEach = parseForEach(attributeValue);
            } else if ("v-text".equals(attributeName)) {
                textExpression = createExpression(attributeValue);
            } else if ("v-html".equals(attributeName)) {
                htmlExpression = createExpression(attributeValue);
            } else if (attributeName.startsWith("v-bind:")) {
                boundAttributes.put(attributeName.substring("v-bind:".length()), createExpression(attributeValue));
            } else if (attributeName.startsWith(":")) {
                boundAttributes.put(attributeName.substring(1), createExpression(attributeValue));
            } else {
                literalAttributes.put(attributeName, attributeValue);
            }
        }

        if (textExpression != null && htmlExpression != null) {
            throw new MacroPreparationException("An element cannot define both v-text and v-html.");
        }

        return new ElementDefinition(tagName,
            new CommonDirectives(ifExpression, forEach, textExpression, htmlExpression), literalAttributes,
            boundAttributes);
    }

    private JexlExpression createExpression(String source) throws MacroPreparationException
    {
        if (source == null || source.isBlank()) {
            throw new MacroPreparationException("A JEXL expression cannot be empty.");
        }
        try {
            return this.jexlEngineManager.createExpression(source);
        } catch (JexlException e) {
            throw new MacroPreparationException("Failed to compile JEXL expression: " + source, e);
        }
    }

    private ForEachDefinition parseForEach(String source) throws MacroPreparationException
    {
        Matcher matcher = FOR_PATTERN.matcher(source == null ? "" : source);
        if (!matcher.matches()) {
            throw new MacroPreparationException("Invalid v-for expression: " + source);
        }

        String variables = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        List<String> parts = new ArrayList<>();
        for (String part : variables.split(",")) {
            if (!part.isBlank()) {
                parts.add(normalizeVariableName(part.trim()));
            }
        }
        if (parts.isEmpty()) {
            throw new MacroPreparationException("The v-for expression must declare at least one variable.");
        }
        if (parts.size() > 2) {
            throw new MacroPreparationException("The v-for expression supports at most two variables.");
        }

        return new ForEachDefinition(parts.get(0), parts.size() == 2 ? parts.get(1) : null,
            createExpression(matcher.group(3)));
    }

    private void executeScripts(PreparedTemplate template, OverlayJexlContext rootContext, Map<String, Object> templateValues)
        throws MacroExecutionException
    {
        for (ScriptNode scriptNode : template.scripts) {
            Object result;
            try {
                result = this.jexlEngineManager.evaluate(scriptNode.script, new OverlayJexlContext(rootContext));
            } catch (JexlException e) {
                throw new MacroExecutionException("Failed to execute a JEXL template script.", e);
            }

            if (result == null) {
                continue;
            }
            if (!(result instanceof Map<?, ?> returnedValues)) {
                throw new MacroExecutionException("The JEXL template script must return a map or null.");
            }
            for (Map.Entry<?, ?> entry : returnedValues.entrySet()) {
                templateValues.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
    }

    private void renderNodes(List<TemplateNode> nodes, OverlayJexlContext context, XDOMGeneratorListener listener,
        Map<String, FragmentNode> fragments) throws MacroExecutionException
    {
        for (TemplateNode node : nodes) {
            node.render(context, listener, fragments);
        }
    }

    private Object evaluateExpression(JexlExpression expression, JexlContext context) throws MacroExecutionException
    {
        try {
            return this.jexlEngineManager.evaluate(expression, context);
        } catch (JexlException e) {
            throw new MacroExecutionException("Failed to evaluate a JEXL template expression.", e);
        }
    }

    private Map<String, String> evaluateAttributes(Map<String, String> literalAttributes,
        Map<String, JexlExpression> boundAttributes, OverlayJexlContext context) throws MacroExecutionException
    {
        Map<String, String> result = new LinkedHashMap<>(literalAttributes);
        for (Map.Entry<String, JexlExpression> entry : boundAttributes.entrySet()) {
            String attributeName = entry.getKey();
            Object value = evaluateExpression(entry.getValue(), context);
            if (Attribute.isBooleanAttribute(attributeName)) {
                if (toBoolean(value)) {
                    result.put(attributeName, "");
                } else {
                    result.remove(attributeName);
                }
            } else if (value == null) {
                result.remove(attributeName);
            } else {
                result.put(attributeName, String.valueOf(value));
            }
        }
        return result;
    }

    private boolean toBoolean(Object value)
    {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }
        if (value instanceof CharSequence sequence) {
            return !sequence.toString().isBlank() && !"false".equalsIgnoreCase(sequence.toString());
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private Iterable<?> toIterable(Object value) throws MacroExecutionException
    {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable;
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet();
        }
        if (value instanceof Iterator<?> iterator) {
            List<Object> values = new ArrayList<>();
            iterator.forEachRemaining(values::add);
            return values;
        }
        if (value instanceof Enumeration<?> enumeration) {
            List<Object> values = new ArrayList<>();
            while (enumeration.hasMoreElements()) {
                values.add(enumeration.nextElement());
            }
            return values;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                values.add(Array.get(value, i));
            }
            return values;
        }

        throw new MacroExecutionException("The v-for expression must evaluate to an iterable value.");
    }

    private void emitText(String text, XDOMGeneratorListener listener)
    {
        if (text == null || text.isEmpty()) {
            return;
        }
        String normalized = text.replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            listener.onSpace();
            return;
        }

        boolean leadingSpace = normalized.startsWith(" ");
        boolean trailingSpace = normalized.endsWith(" ");
        String trimmed = normalized.trim();
        if (leadingSpace) {
            listener.onSpace();
        }
        if (!trimmed.isEmpty()) {
            String[] words = trimmed.split(" ");
            for (int i = 0; i < words.length; i++) {
                if (i > 0) {
                    listener.onSpace();
                }
                listener.onWord(words[i]);
            }
        }
        if (trailingSpace) {
            listener.onSpace();
        }
    }

    private ResourceReference toResourceReference(String value)
    {
        String reference = Objects.requireNonNullElse(value, "");
        Matcher matcher = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*)(~?):(.*)$").matcher(reference);
        if (matcher.matches()) {
            String scheme = matcher.group(1);
            ResourceType resourceType = switch (scheme) {
                case "doc" -> ResourceType.DOCUMENT;
                case "page" -> ResourceType.PAGE;
                case "attach" -> ResourceType.ATTACHMENT;
                case "pageAttach" -> ResourceType.PAGE_ATTACHMENT;
                case "icon" -> ResourceType.ICON;
                case "interwiki" -> ResourceType.INTERWIKI;
                case "mailto" -> ResourceType.MAILTO;
                case "path" -> ResourceType.PATH;
                case "space" -> ResourceType.SPACE;
                case "user" -> ResourceType.USER;
                case "data" -> ResourceType.DATA;
                case "unc" -> ResourceType.UNC;
                default -> null;
            };
            if (resourceType != null) {
                ResourceReference resourceReference = new ResourceReference(matcher.group(3), resourceType);
                resourceReference.setTyped(true);
                return resourceReference;
            }
            if (URL_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                ResourceReference resourceReference = new ResourceReference(reference, ResourceType.URL);
                resourceReference.setTyped(false);
                return resourceReference;
            }
        }

        ResourceReference resourceReference = new ResourceReference(reference, ResourceType.URL);
        resourceReference.setTyped(false);
        return resourceReference;
    }

    private String buildStartTag(String tagName, Map<String, String> attributes, boolean selfClosing)
    {
        StringBuilder builder = new StringBuilder();
        builder.append('<').append(tagName);
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            builder.append(' ').append(entry.getKey());
            if (entry.getValue() != null) {
                builder.append("=\"").append(escapeAttribute(entry.getValue())).append('"');
            }
        }
        if (selfClosing) {
            builder.append(" />");
        } else {
            builder.append('>');
        }
        return builder.toString();
    }

    private String buildEndTag(String tagName)
    {
        return "</" + tagName + '>';
    }

    private String escapeAttribute(String value)
    {
        return value.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private String normalizeVariableName(String variableName)
    {
        String normalized = variableName == null ? "" : variableName.trim();
        while (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private interface TemplateNode
    {
        void render(OverlayJexlContext context, XDOMGeneratorListener listener, Map<String, FragmentNode> fragments)
            throws MacroExecutionException;
    }

    private abstract class DirectiveNode implements TemplateNode
    {
        protected final CommonDirectives directives;

        DirectiveNode(CommonDirectives directives)
        {
            this.directives = directives;
        }

        @Override
        public final void render(OverlayJexlContext context, XDOMGeneratorListener listener, Map<String, FragmentNode> fragments)
            throws MacroExecutionException
        {
            if (this.directives.ifExpression != null
                && !toBoolean(evaluateExpression(this.directives.ifExpression, context))) {
                return;
            }

            if (this.directives.forEach != null) {
                int index = 0;
                for (Object value : toIterable(evaluateExpression(this.directives.forEach.expression, context))) {
                    OverlayJexlContext itemContext = new OverlayJexlContext(context);
                    itemContext.set(this.directives.forEach.variableName, value);
                    if (this.directives.forEach.indexName != null) {
                        itemContext.set(this.directives.forEach.indexName, index);
                    }
                    renderOnce(itemContext, listener, fragments);
                    index++;
                }
            } else {
                renderOnce(context, listener, fragments);
            }
        }

        abstract void renderOnce(OverlayJexlContext context, XDOMGeneratorListener listener,
            Map<String, FragmentNode> fragments) throws MacroExecutionException;
    }

    private final class TextNode implements TemplateNode
    {
        private final String content;

        private TextNode(String content)
        {
            this.content = content;
        }

        @Override
        public void render(OverlayJexlContext context, XDOMGeneratorListener listener, Map<String, FragmentNode> fragments)
        {
            emitText(this.content, listener);
        }
    }

    private final class SetNode extends DirectiveNode
    {
        private final String variableName;

        private final JexlExpression valueExpression;

        private SetNode(CommonDirectives directives, String variableName, JexlExpression valueExpression)
        {
            super(directives);
            this.variableName = variableName;
            this.valueExpression = valueExpression;
        }

        @Override
        void renderOnce(OverlayJexlContext context, XDOMGeneratorListener listener, Map<String, FragmentNode> fragments)
            throws MacroExecutionException
        {
            context.set(this.variableName, evaluateExpression(this.valueExpression, context));
        }
    }

    private final class RenderFragmentNode extends DirectiveNode
    {
        private final String fragmentName;

        private final Map<String, JexlExpression> boundAttributes;

        private RenderFragmentNode(CommonDirectives directives, String fragmentName,
            Map<String, JexlExpression> boundAttributes)
        {
            super(directives);
            this.fragmentName = fragmentName;
            this.boundAttributes = boundAttributes;
        }

        @Override
        void renderOnce(OverlayJexlContext context, XDOMGeneratorListener listener, Map<String, FragmentNode> fragments)
            throws MacroExecutionException
        {
            FragmentNode fragment = fragments.get(this.fragmentName);
            if (fragment == null) {
                throw new MacroExecutionException("Unknown fragment: " + this.fragmentName);
            }
            OverlayJexlContext fragmentContext = new OverlayJexlContext(context);
            for (Map.Entry<String, JexlExpression> entry : this.boundAttributes.entrySet()) {
                fragmentContext.set(entry.getKey(), evaluateExpression(entry.getValue(), context));
            }
            renderNodes(fragment.nodes, fragmentContext, listener, fragments);
        }
    }

    private final class MacroNode extends DirectiveNode
    {
        private final String macroId;

        private final Map<String, String> literalAttributes;

        private final Map<String, JexlExpression> boundAttributes;

        private final List<TemplateNode> children;

        private MacroNode(CommonDirectives directives, String macroId, Map<String, String> literalAttributes,
            Map<String, JexlExpression> boundAttributes, List<TemplateNode> children)
        {
            super(directives);
            this.macroId = macroId;
            this.literalAttributes = literalAttributes;
            this.boundAttributes = boundAttributes;
            this.children = children;
        }

        @Override
        void renderOnce(OverlayJexlContext context, XDOMGeneratorListener listener, Map<String, FragmentNode> fragments)
            throws MacroExecutionException
        {
            StringBuilder content = new StringBuilder();
            for (TemplateNode child : this.children) {
                if (child instanceof TextNode textNode) {
                    content.append(textNode.content);
                }
            }
            listener.onMacro(this.macroId, evaluateAttributes(this.literalAttributes, this.boundAttributes, context),
                content.toString().trim(), false);
        }
    }

    private final class ImageNode extends DirectiveNode
    {
        private final Map<String, String> literalAttributes;

        private final Map<String, JexlExpression> boundAttributes;

        private ImageNode(CommonDirectives directives, Map<String, String> literalAttributes,
            Map<String, JexlExpression> boundAttributes)
        {
            super(directives);
            this.literalAttributes = literalAttributes;
            this.boundAttributes = boundAttributes;
        }

        @Override
        void renderOnce(OverlayJexlContext context, XDOMGeneratorListener listener, Map<String, FragmentNode> fragments)
            throws MacroExecutionException
        {
            Map<String, String> attributes = new LinkedHashMap<>(evaluateAttributes(this.literalAttributes,
                this.boundAttributes, context));
            String source = attributes.remove("src");
            if (source == null || source.isBlank()) {
                throw new MacroExecutionException("The xw-img tag requires a src attribute.");
            }
            listener.onImage(toResourceReference(source), false, attributes);
        }
    }

    private final class ElementNode extends DirectiveNode
    {
        private final String tagName;

        private final Map<String, String> literalAttributes;

        private final Map<String, JexlExpression> boundAttributes;

        private final List<TemplateNode> children;

        private ElementNode(CommonDirectives directives, String tagName, Map<String, String> literalAttributes,
            Map<String, JexlExpression> boundAttributes, List<TemplateNode> children)
        {
            super(directives);
            this.tagName = tagName;
            this.literalAttributes = literalAttributes;
            this.boundAttributes = boundAttributes;
            this.children = children;
        }

        @Override
        void renderOnce(OverlayJexlContext context, XDOMGeneratorListener listener, Map<String, FragmentNode> fragments)
            throws MacroExecutionException
        {
            Map<String, String> attributes = evaluateAttributes(this.literalAttributes, this.boundAttributes, context);
            OverlayJexlContext childContext = new OverlayJexlContext(context);

            if ("template".equals(this.tagName)) {
                renderElementBody(childContext, listener, fragments);
                return;
            }

            switch (this.tagName) {
                case "p" -> renderParagraph(attributes, childContext, listener, fragments);
                case "ul" -> renderList(ListType.BULLETED, attributes, childContext, listener, fragments);
                case "ol" -> renderList(ListType.NUMBERED, attributes, childContext, listener, fragments);
                case "li" -> renderListItem(attributes, childContext, listener, fragments);
                case "strong", "b" -> renderFormat(Format.BOLD, attributes, childContext, listener, fragments);
                case "em", "i" -> renderFormat(Format.ITALIC, attributes, childContext, listener, fragments);
                case "u" -> renderFormat(Format.UNDERLINED, attributes, childContext, listener, fragments);
                case "s", "strike" -> renderFormat(Format.STRIKEDOUT, attributes, childContext, listener, fragments);
                case "sub" -> renderFormat(Format.SUBSCRIPT, attributes, childContext, listener, fragments);
                case "sup" -> renderFormat(Format.SUPERSCRIPT, attributes, childContext, listener, fragments);
                case "code" -> renderFormat(Format.MONOSPACE, attributes, childContext, listener, fragments);
                case "a" -> renderLink(attributes, childContext, listener, fragments);
                case "img" -> renderImage(attributes, listener);
                case "br" -> listener.onNewLine();
                case "hr" -> listener.onHorizontalLine(attributes);
                default -> renderRawElement(attributes, childContext, listener, fragments);
            }
        }

        private void renderParagraph(Map<String, String> attributes, OverlayJexlContext childContext,
            XDOMGeneratorListener listener, Map<String, FragmentNode> fragments) throws MacroExecutionException
        {
            listener.beginParagraph(attributes);
            renderElementBody(childContext, listener, fragments);
            listener.endParagraph(attributes);
        }

        private void renderList(ListType listType, Map<String, String> attributes, OverlayJexlContext childContext,
            XDOMGeneratorListener listener, Map<String, FragmentNode> fragments) throws MacroExecutionException
        {
            listener.beginList(listType, attributes);
            renderElementBody(childContext, listener, fragments);
            listener.endList(listType, attributes);
        }

        private void renderListItem(Map<String, String> attributes, OverlayJexlContext childContext,
            XDOMGeneratorListener listener, Map<String, FragmentNode> fragments) throws MacroExecutionException
        {
            listener.beginListItem(attributes);
            renderElementBody(childContext, listener, fragments);
            listener.endListItem(attributes);
        }

        private void renderFormat(Format format, Map<String, String> attributes, OverlayJexlContext childContext,
            XDOMGeneratorListener listener, Map<String, FragmentNode> fragments) throws MacroExecutionException
        {
            listener.beginFormat(format, attributes);
            renderElementBody(childContext, listener, fragments);
            listener.endFormat(format, attributes);
        }

        private void renderLink(Map<String, String> attributes, OverlayJexlContext childContext,
            XDOMGeneratorListener listener, Map<String, FragmentNode> fragments) throws MacroExecutionException
        {
            String href = attributes.remove("href");
            if (href == null || href.isBlank()) {
                throw new MacroExecutionException("The a tag requires an href attribute.");
            }
            listener.beginLink(toResourceReference(href), false, attributes);
            renderElementBody(childContext, listener, fragments);
            listener.endLink(toResourceReference(href), false, attributes);
        }

        private void renderImage(Map<String, String> attributes, XDOMGeneratorListener listener)
            throws MacroExecutionException
        {
            String source = attributes.remove("src");
            if (source == null || source.isBlank()) {
                throw new MacroExecutionException("The img tag requires a src attribute.");
            }
            listener.onImage(toResourceReference(source), false, attributes);
        }

        private void renderRawElement(Map<String, String> attributes, OverlayJexlContext childContext,
            XDOMGeneratorListener listener, Map<String, FragmentNode> fragments) throws MacroExecutionException
        {
            boolean selfClosing = VOID_ELEMENTS.contains(this.tagName) && this.directives.textExpression == null
                && this.directives.htmlExpression == null && this.children.isEmpty();
            listener.onRawText(buildStartTag(this.tagName, attributes, selfClosing), RAW_SYNTAX);
            if (!selfClosing) {
                renderElementBody(childContext, listener, fragments);
                listener.onRawText(buildEndTag(this.tagName), RAW_SYNTAX);
            }
        }

        private void renderElementBody(OverlayJexlContext childContext, XDOMGeneratorListener listener,
            Map<String, FragmentNode> fragments) throws MacroExecutionException
        {
            if (this.directives.textExpression != null) {
                Object value = evaluateExpression(this.directives.textExpression, childContext);
                if (value != null) {
                    emitText(String.valueOf(value), listener);
                }
            } else if (this.directives.htmlExpression != null) {
                Object value = evaluateExpression(this.directives.htmlExpression, childContext);
                if (value != null) {
                    listener.onRawText(String.valueOf(value), RAW_SYNTAX);
                }
            } else {
                renderNodes(this.children, childContext, listener, fragments);
            }
        }
    }

    private static final class PreparedTemplate
    {
        private final List<TemplateNode> bodyNodes;

        private final List<ScriptNode> scripts;

        private final Map<String, FragmentNode> fragments;

        private PreparedTemplate(List<TemplateNode> bodyNodes, List<ScriptNode> scripts,
            Map<String, FragmentNode> fragments)
        {
            this.bodyNodes = bodyNodes;
            this.scripts = scripts;
            this.fragments = fragments;
        }
    }

    private static final class FragmentNode
    {
        private final List<TemplateNode> nodes;

        private FragmentNode(List<TemplateNode> nodes)
        {
            this.nodes = nodes;
        }
    }

    private static final class ScriptNode
    {
        private final JexlScript script;

        private ScriptNode(JexlScript script)
        {
            this.script = script;
        }
    }

    private static final class CommonDirectives
    {
        private final JexlExpression ifExpression;

        private final ForEachDefinition forEach;

        private final JexlExpression textExpression;

        private final JexlExpression htmlExpression;

        private CommonDirectives(JexlExpression ifExpression, ForEachDefinition forEach, JexlExpression textExpression,
            JexlExpression htmlExpression)
        {
            this.ifExpression = ifExpression;
            this.forEach = forEach;
            this.textExpression = textExpression;
            this.htmlExpression = htmlExpression;
        }
    }

    private static final class ForEachDefinition
    {
        private final String variableName;

        private final String indexName;

        private final JexlExpression expression;

        private ForEachDefinition(String variableName, String indexName, JexlExpression expression)
        {
            this.variableName = variableName;
            this.indexName = indexName;
            this.expression = expression;
        }
    }

    private static final class ElementDefinition
    {
        private final String tagName;

        private final CommonDirectives common;

        private final Map<String, String> literalAttributes;

        private final Map<String, JexlExpression> boundAttributes;

        private ElementDefinition(String tagName, CommonDirectives common, Map<String, String> literalAttributes,
            Map<String, JexlExpression> boundAttributes)
        {
            this.tagName = tagName;
            this.common = common;
            this.literalAttributes = literalAttributes;
            this.boundAttributes = boundAttributes;
        }
    }
}
