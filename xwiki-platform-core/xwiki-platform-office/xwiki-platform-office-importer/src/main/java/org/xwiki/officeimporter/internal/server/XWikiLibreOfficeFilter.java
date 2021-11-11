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
package org.xwiki.officeimporter.internal.server;

import org.jodconverter.core.office.OfficeContext;
import org.jodconverter.local.filter.Filter;
import org.jodconverter.local.filter.FilterChain;
import org.jodconverter.local.office.utils.Lo;
import org.jodconverter.local.office.utils.UnoRuntime;
import org.jodconverter.local.office.utils.Write;

import com.sun.star.beans.XPropertySet;
import com.sun.star.container.XIndexAccess;
import com.sun.star.container.XNameAccess;
import com.sun.star.container.XNameContainer;
import com.sun.star.lang.XComponent;
import com.sun.star.style.XStyle;
import com.sun.star.style.XStyleFamiliesSupplier;
import com.sun.star.text.XDocumentIndex;
import com.sun.star.text.XDocumentIndexesSupplier;
import com.sun.star.text.XParagraphCursor;
import com.sun.star.text.XText;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextTable;
import com.sun.star.text.XTextTablesSupplier;

/**
 * Various XWiki-specific filters.
 * @version $Id $
 * @since 14.0RC1
 */
public class XWikiLibreOfficeFilter implements Filter
{
    /**
     * Marker that is placed at the beginning of a heading that is a numbered paragraph.
     */
    public static final String PARAGRAPH_NUMBERING_MARKER = "[[NUMBERED_PARAGRAPH_START]]";
    private static final String PARA_STYLE_NAME = "ParaStyleName";
    private static final String NUMBERING_STYLE_NAME = "NumberingStyleName";
    private static final String STYLE_STANDARD = "Standard";

    /**
     * Apply the filter.
     *
     * @param officeContext The Open Office context
     * @param xComponent The component with the document
     * @param filterChain The filter chain
     * @throws Exception The filter may fail.
     */
    @Override public void doFilter(@org.checkerframework.checker.nullness.qual.NonNull OfficeContext officeContext,
        @org.checkerframework.checker.nullness.qual.NonNull XComponent xComponent,
        @org.checkerframework.checker.nullness.qual.NonNull FilterChain filterChain) throws Exception
    {
        if (Write.isText(xComponent)) {
            XTextDocument textDocument = Write.getTextDoc(xComponent);
            assert textDocument != null;

            adaptHeadings(textDocument);

            addMarginZeroTagsInTables(xComponent);
            removeIndices(xComponent);

            removeHeaderFooter(textDocument);
        }

        // Invoke the next filter in the chain
        filterChain.doFilter(officeContext, xComponent);
    }

    private static void removeHeaderFooter(XTextDocument textDocument) throws Exception
    {
        UnoRuntime runtime = UnoRuntime.getInstance();

        // Adapted from https://stackoverflow.com/questions/37492362/openoffice-api-how-to-turn-off-headers-and-footers
        XStyleFamiliesSupplier xStyleFamiliesSupplier =
            runtime.queryInterface(XStyleFamiliesSupplier.class, textDocument);
        XNameAccess nameAcc = xStyleFamiliesSupplier.getStyleFamilies();
        XNameContainer pageStyles = runtime.queryInterface(XNameContainer.class, nameAcc.getByName("PageStyles"));

        for (String styleName : pageStyles.getElementNames()) {
            XStyle pageStyle = runtime.queryInterface(XStyle.class, pageStyles.getByName(styleName));
            XPropertySet pageStyleProperties = runtime.queryInterface(XPropertySet.class, pageStyle);
            pageStyleProperties.setPropertyValue("HeaderIsOn", Boolean.FALSE);
            pageStyleProperties.setPropertyValue("FooterIsOn", Boolean.FALSE);
        }
    }

    private static void adaptHeadings(XTextDocument textDocument) throws Exception
    {
        boolean isNumberedParagraphDocument = isNumberedParagraphDocument(textDocument);

        XText text = textDocument.getText();
        XTextCursor textCursor = text.createTextCursor();
        XParagraphCursor paragraphCursor = Lo.qi(XParagraphCursor.class, textCursor);
        paragraphCursor.gotoStart(false);
        do {
            XPropertySet xCursorProperties = UnoRuntime.getInstance().queryInterface(XPropertySet.class,
                paragraphCursor);

            String styleName = (String) xCursorProperties.getPropertyValue(PARA_STYLE_NAME);

            addMarginZeroTag(text, paragraphCursor);

            if (isHeading(styleName)) {
                short charCase = (short) xCursorProperties.getPropertyValue("CharCaseMap");
                // Transform headings to upper case if they are not already but should be
                /*
                if (charCase == CaseMap.UPPERCASE) {
                    paragraphCursor.gotoEndOfParagraph(true);
                    String headingText = paragraphCursor.getString();
                    String headingUpperText = headingText.toUpperCase();
                    if (!headingText.equals(headingUpperText)) {
                        text.insertString(paragraphCursor, headingText.toUpperCase(), true);
                    }
                }
                */
                if (isNumberedParagraphDocument) {
                    String numberingStyle = (String) xCursorProperties.getPropertyValue(NUMBERING_STYLE_NAME);
                    if (numberingStyle != null && numberingStyle.equals("Outline")) {
                        text.insertString(paragraphCursor, PARAGRAPH_NUMBERING_MARKER, false);
                    } else {
                        xCursorProperties.setPropertyValue(PARA_STYLE_NAME, "Text Body");
                        //text.insertString(paragraphCursor, "[[SKIP_NUMBERING]]", false);
                    }
                } else {
                    xCursorProperties.setPropertyValue(NUMBERING_STYLE_NAME, "");
                    //text.insertString(paragraphCursor, "[[HEADING_START]]", false);
                }
            }
        } while (paragraphCursor.gotoNextParagraph(false));
    }

    private static void addMarginZeroTagsInTables(XComponent document) throws Exception
    {
        XTextTablesSupplier textTablesSupplier = Lo.qi(XTextTablesSupplier.class, document);
        final XNameAccess textTables = textTablesSupplier.getTextTables();
        for (String tableName : textTables.getElementNames()) {
            XTextTable table = Lo.qi(XTextTable.class, textTables.getByName(tableName));
            for (String cellName : table.getCellNames()) {
                XText cellText = UnoRuntime.getInstance().queryInterface(XText.class, table.getCellByName(cellName));
                XTextCursor cellCursor = cellText.createTextCursor();
                XParagraphCursor paragraphCursor = Lo.qi(XParagraphCursor.class, cellCursor);
                paragraphCursor.gotoStart(false);

                do {
                    addMarginZeroTag(cellText, paragraphCursor);
                } while (paragraphCursor.gotoNextParagraph(false));
            }
        }
    }

    private static void addMarginZeroTag(XText text, XParagraphCursor cursor) throws Exception
    {
        XPropertySet xCursorProperties = UnoRuntime.getInstance().queryInterface(XPropertySet.class,
            cursor);

        String styleName = (String) xCursorProperties.getPropertyValue(PARA_STYLE_NAME);
        int bottomMargin = (int) xCursorProperties.getPropertyValue("ParaBottomMargin");
        String numberingStyle = (String) xCursorProperties.getPropertyValue(NUMBERING_STYLE_NAME);

        if (styleName.equals(STYLE_STANDARD) && bottomMargin < 100 && numberingStyle.isEmpty()) {
            cursor.gotoEndOfParagraph(true);
            String paragraphContent = cursor.getString();
            cursor.gotoStartOfParagraph(false);

            // Create an independent cursor to peek at the next paragraph
            XTextCursor textCursor = text.createTextCursor();
            XParagraphCursor nextCursor = Lo.qi(XParagraphCursor.class, textCursor);
            nextCursor.gotoRange(cursor, false);
            if (!nextCursor.gotoNextParagraph(false)) {
                // we are at the end of the document
                return;
            }
            nextCursor.gotoEndOfParagraph(true);
            String nextParagraphContent = nextCursor.getString();
            XPropertySet nextProperties = UnoRuntime.getInstance().queryInterface(XPropertySet.class,
                nextCursor);
            String nextStyle = (String) nextProperties.getPropertyValue(PARA_STYLE_NAME);

            // Insert the tag only if the paragraph is not otherwise empty.
            if (!paragraphContent.trim().isEmpty() && !nextParagraphContent.isEmpty()
                && nextStyle.equals(STYLE_STANDARD))
            {
                text.insertString(cursor, "[[MARGIN_BOTTOM_0]]", false);
            }
        }

    }

    private static void removeIndices(final XComponent document) throws Exception
    {
        // Get the DocumentIndexesSupplier interface of the document
        final XDocumentIndexesSupplier documentIndexesSupplier =
            Lo.qi(XDocumentIndexesSupplier.class, document);

        // Get an XIndexAccess of DocumentIndexes
        final XIndexAccess documentIndexes =
            Lo.qi(XIndexAccess.class, documentIndexesSupplier.getDocumentIndexes());

        for (int i = 0; i < documentIndexes.getCount(); i++) {
            // Remove each index
            final XDocumentIndex docIndex = Lo.qi(XDocumentIndex.class, documentIndexes.getByIndex(i));
            docIndex.getAnchor().setString("[[TABLE_OF_CONTENTS]]");
            //docIndex.dispose();
        }
    }

    private static boolean isNumberedParagraphDocument(XTextDocument textDocument) throws Exception
    {
        // 1. get the style families
        XStyleFamiliesSupplier xSupplier = Lo.qi(XStyleFamiliesSupplier.class, textDocument);
        XNameAccess nameAcc = xSupplier.getStyleFamilies();

        // 2. get the paragraph style family
        XNameContainer paraStyleCon = Lo.qi(XNameContainer.class, nameAcc.getByName("ParagraphStyles"));

        // 3. get the 'Heading 1' style (property set)
        XPropertySet headingProperties = Lo.qi(XPropertySet.class, paraStyleCon.getByName("Heading 1"));
        String nextStyle = (String) headingProperties.getPropertyValue("FollowStyle");
        return isHeading(nextStyle);
    }

    private static boolean isHeading(String styleName)
    {
        return styleName.startsWith("Heading");
    }
}
