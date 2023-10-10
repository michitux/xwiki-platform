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
package org.xwiki.platform.security.requiredrights.internal;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.internal.parser.plain.PlainTextBlockParser;
import org.xwiki.rendering.internal.parser.plain.PlainTextStreamParser;
import org.xwiki.rendering.internal.renderer.event.EventBlockRenderer;
import org.xwiki.rendering.internal.renderer.event.EventRenderer;
import org.xwiki.rendering.internal.renderer.event.EventRendererFactory;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.StringProperty;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.StringClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link XObjectDisplayerProvider}.
 * 
 * @version $Id$
 */
@ComponentTest
@ComponentList({
    EventBlockRenderer.class,
    EventRendererFactory.class,
    EventRenderer.class,
    PlainTextBlockParser.class,
    PlainTextStreamParser.class
})
class XObjectDisplayerProviderTest
{
    @Inject
    @Named("event/1.0")
    private BlockRenderer eventRenderer;

    @InjectMockComponents
    private XObjectDisplayerProvider provider;

    @Test
    void getWithNormalProperties()
    {
        // Create a mock object with an XClass that has a TextArea property and a String property
        BaseObject object = mock();
        
        BaseClass xClass = mock();
        when(object.getXClass(any())).thenReturn(xClass);

        TextAreaClass textAreaProperty = mock();
        when(textAreaProperty.getTranslatedPrettyName(any())).thenReturn("Text Area");
        String textAreaName = "textAreaName";
        when(textAreaProperty.getName()).thenReturn(textAreaName);
        when(object.getStringValue(textAreaName)).thenReturn("textAreaValue");

        StringClass stringProperty = mock();
        when(stringProperty.getTranslatedPrettyName(any())).thenReturn("String");
        String stringName = "stringName";
        when(stringProperty.getName()).thenReturn(stringName);
        when(object.getStringValue(stringName)).thenReturn("stringValue");
        when(stringProperty.displayView(eq(stringName), eq(object), any())).thenReturn("displayedStringValue");

        when(xClass.getProperties()).thenReturn(new Object[] {textAreaProperty, stringProperty});
        when(xClass.getDeprecatedObjectProperties(object)).thenReturn(List.of());

        Block block = this.provider.get(object).get();
        DefaultWikiPrinter printer = new DefaultWikiPrinter();
        this.eventRenderer.render(block, printer);

        List<String> lines = List.of(
            "beginDefinitionList",
            "beginDefinitionTerm",
            "onWord [Text]",
            "onSpace",
            "onWord [Area]",
            "beginFormat [NONE] [[class]=[xHint]]",
            "endFormat [NONE] [[class]=[xHint]]",
            "endDefinitionTerm",
            "beginDefinitionDescription",
            "beginGroup [[class]=[code box]]",
            "onWord [textAreaValue]",
            "endGroup [[class]=[code box]]",
            "endDefinitionDescription",
            "beginDefinitionTerm",
            "onWord [String]",
            "beginFormat [NONE] [[class]=[xHint]]",
            "endFormat [NONE] [[class]=[xHint]]",
            "endDefinitionTerm",
            "beginDefinitionDescription",
            "onRawText [displayedStringValue] [html/5.0]",
            "endDefinitionDescription",
            "endDefinitionList",
            ""
        );

        assertEquals(String.join("\n", lines), printer.toString());
    }

    @Test
    void getWithDeprecatedProperties()
    {
        BaseObject object = mock();

        BaseClass xClass = mock();
        when(object.getXClass(any())).thenReturn(xClass);

        when(xClass.getProperties()).thenReturn(new Object[] {});
        StringProperty deprecatedProperty = mock();
        when(deprecatedProperty.getName()).thenReturn("deprecatedName");
        when(deprecatedProperty.getValue()).thenReturn("deprecatedValue");
        when(xClass.getDeprecatedObjectProperties(object)).thenReturn(List.of(deprecatedProperty));

        Block block = this.provider.get(object).get();
        DefaultWikiPrinter printer = new DefaultWikiPrinter();
        this.eventRenderer.render(block, printer);

        List<String> lines = List.of(
            "beginDefinitionList",
            "endDefinitionList",
            "beginGroup [[class]=[box warningmessage deprecatedProperties]]",
            "beginDefinitionList",
            "beginDefinitionTerm",
            "onWord [deprecatedName]",
            "beginFormat [NONE] [[class]=[xHint]]",
            "endFormat [NONE] [[class]=[xHint]]",
            "endDefinitionTerm",
            "beginDefinitionDescription",
            "beginGroup [[class]=[code box]]",
            "onWord [deprecatedValue]",
            "endGroup [[class]=[code box]]",
            "endDefinitionDescription",
            "endDefinitionList",
            "endGroup [[class]=[box warningmessage deprecatedProperties]]",
            ""
        );

        assertEquals(String.join("\n", lines), printer.toString());
    }
}
