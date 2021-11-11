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
package org.xwiki.officeimporter.internal.filter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xwiki.component.annotation.Component;
import org.xwiki.xml.html.filter.AbstractHTMLFilter;

/**
 * Transforms paragraph numbering type documents to use the paragraph numbering macro.
 *
 * @since 13.10RC1
 * @version $Id$
 */
@Component
@Named("officeimporter/paragraphnumbering")
@Singleton
public class ParagraphNumberingFilter extends AbstractHTMLFilter
{
    private static final String PARAGRAPH_NUMBERING_MARKER = "[[NUMBERED_PARAGRAPH_START]]";

    @Override public void filter(Document document, Map<String, String> cleaningParameters)
    {
        NodeList headings = null;
        try {
            headings = getAllParagraphHeadings(document);
        } catch (Exception e) {
            assert false;
        }

        Deque<Element> orderedParagraphContainers = new ArrayDeque<>();
        Node previousHeadingNextSibling = null;

        for (int i = 0; i < headings.getLength(); ++i) {
            Element heading = (Element) headings.item(i);
            if (heading.getTextContent().contains(PARAGRAPH_NUMBERING_MARKER)) {
                String headingText = heading.getTextContent();
                int markerPosition = headingText.indexOf(PARAGRAPH_NUMBERING_MARKER);
                String headingNumber = headingText.substring(0, markerPosition);
                String[] headingNumberSegments = headingNumber.split("\\.");

                if (previousHeadingNextSibling != null) {
                    assert !orderedParagraphContainers.isEmpty();
                    moveToContentDiv(document, orderedParagraphContainers.peek().getLastChild().getFirstChild(),
                        previousHeadingNextSibling, heading);
                }

                adjustOrderedParagraphsLevel(document, heading, orderedParagraphContainers,
                    headingNumberSegments.length);

                previousHeadingNextSibling = heading.getNextSibling();
                Element listElement = document.createElement(TAG_LI);
                assert orderedParagraphContainers.peek() != null;
                orderedParagraphContainers.peek().appendChild(listElement);
                Element divElement = document.createElement(TAG_DIV);
                listElement.appendChild(divElement);
                Element headingContainer = document.createElement(TAG_P);
                divElement.appendChild(headingContainer);
                moveChildren(heading, headingContainer);
                heading.getParentNode().removeChild(heading);
            }
        }

        if (!orderedParagraphContainers.isEmpty()) {
            moveToContentDiv(document, orderedParagraphContainers.peek().getLastChild().getFirstChild(),
                previousHeadingNextSibling,
                null);
        }
    }

    private NodeList getAllParagraphHeadings(Document document) throws Exception
    {
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();
        XPathExpression expr =
            xPath.compile("/html/body//*[(self::h1 or self::h2 or self::h3 or self::h4 or self::h5 "
                + "or self::h6) and contains(., '" + PARAGRAPH_NUMBERING_MARKER + "')]");
        return (NodeList) expr.evaluate(document, XPathConstants.NODESET);
    }

    private void adjustOrderedParagraphsLevel(Document document,
        Element heading, Deque<Element> orderedParagraphContainers, int level)
    {
        while (orderedParagraphContainers.size() < level) {
            Element nextContainer = document.createElement(TAG_OL);

            if (orderedParagraphContainers.isEmpty()) {
                heading.getParentNode().insertBefore(nextContainer, heading);
            } else {
                Element parentOL = orderedParagraphContainers.peek();
                if (!parentOL.hasChildNodes()) {
                    parentOL.appendChild(document.createElement(TAG_LI));
                }
                parentOL.getLastChild().appendChild(nextContainer);
            }

            orderedParagraphContainers.push(nextContainer);
        }

        while (orderedParagraphContainers.size() > level) {
            orderedParagraphContainers.pop();
        }

    }

    private void moveToContentDiv(Document document, Node listElement, Node startElement, Node endElement)
    {
        //Node contentDiv = null;
        Node currentElement = startElement;
        while (currentElement != null && !currentElement.isSameNode(endElement)) {
            Node next = currentElement.getNextSibling();

            /*
            // Do not create the content div just for some whitespace.
            if (contentDiv == null && currentElement.getNodeType() == TEXT_NODE
                && currentElement.getTextContent().trim().equals("")) {
                currentElement = next;
                continue;
            }

            if (contentDiv == null) {
                contentDiv = document.createElement(TAG_DIV);
                listElement.appendChild(contentDiv);
            }
             */

            listElement.appendChild(currentElement);
            currentElement = next;
        }
    }
}
