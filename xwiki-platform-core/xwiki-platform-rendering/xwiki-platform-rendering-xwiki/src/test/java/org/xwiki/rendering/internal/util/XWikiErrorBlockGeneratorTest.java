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
package org.xwiki.rendering.internal.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.script.ScriptContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.VerbatimBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.internal.transformation.MutableRenderingContext;
import org.xwiki.rendering.limits.RenderingLimitType;
import org.xwiki.rendering.macro.MacroRenderingLimitExceededException;
import org.xwiki.rendering.transformation.RenderingContext;
import org.xwiki.rendering.util.ErrorBlockGenerator;
import org.xwiki.script.ScriptContextManager;
import org.xwiki.script.internal.CloneableSimpleScriptContext;
import org.xwiki.template.Template;
import org.xwiki.template.TemplateManager;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.AfterComponent;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validate {@link XWikiErrorBlockGenerator}.
 * 
 * @version $Id$
 */
@ComponentTest
class XWikiErrorBlockGeneratorTest
{
    @MockComponent
    private Execution execution;

    @MockComponent
    private TemplateManager templateManager;

    private MutableRenderingContext renderingContext;

    @MockComponent
    private ScriptContextManager scriptContextManager;

    @InjectMockComponents
    private XWikiErrorBlockGenerator errorGenerator;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    private ExecutionContext econtext;

    private ScriptContext scontext;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.ERROR);

    @AfterComponent
    void afterComponent() throws Exception
    {
        this.renderingContext = mock(MutableRenderingContext.class);
        this.componentManager.registerComponent(RenderingContext.class, this.renderingContext);
    }

    @BeforeEach
    void beforeEach()
    {
        this.econtext = new ExecutionContext();
        when(this.execution.getContext()).thenReturn(this.econtext);

        this.scontext = new CloneableSimpleScriptContext();
        when(this.scriptContextManager.getCurrentScriptContext()).thenReturn(this.scontext);
    }

    @Test
    void executeTemplateWithTemplate() throws Exception
    {
        // Try without an id
        Template template1 = mock(Template.class);
        when(this.templateManager.getTemplate(ErrorBlockGenerator.CLASS_ATTRIBUTE_MESSAGE_VALUE + "/default.vm"))
            .thenReturn(template1);
        XDOM xdom = new XDOM(Arrays.asList(new WordBlock("result")));
        when(this.templateManager.execute(template1, false)).thenReturn(xdom);

        List<Block> blocks = this.errorGenerator.generateErrorBlocks(false, null, "message {}", "description {}",
            "value", new Exception("exception"));

        assertSame(xdom.getChildren(), blocks);

        // Try with an id

        Template template2 = mock(Template.class);
        when(this.templateManager.getTemplate(ErrorBlockGenerator.CLASS_ATTRIBUTE_MESSAGE_VALUE + "/id.vm"))
            .thenReturn(template2);
        XDOM xdom2 = new XDOM(Arrays.asList(new WordBlock("result2")));
        when(this.templateManager.execute(template2, false)).thenReturn(xdom2);

        blocks = this.errorGenerator.generateErrorBlocks(false, "id", "message {}", "description {}", "value",
            new Exception("exception"));

        assertSame(xdom2.getChildren(), blocks);
    }

    @Test
    void executeTemplateWithMarker()
    {
        List<Block> blocks = this.errorGenerator.generateErrorBlocks(false, null, "message {}", null, "value");

        assertEquals(1, blocks.size());
        assertEquals("message value.", ((WordBlock) blocks.get(0).getChildren().get(0)).getWord());

        blocks = this.errorGenerator.generateErrorBlocks(false, null, "message {}.", null, "value");

        assertEquals(1, blocks.size());
        assertEquals("message value.", ((WordBlock) blocks.get(0).getChildren().get(0)).getWord());

        blocks = this.errorGenerator.generateErrorBlocks(false, null, "message {}.", "description {}.", "value");

        assertEquals(2, blocks.size());
        assertEquals("message value. Click on this message for details.",
            ((WordBlock) blocks.get(0).getChildren().get(0)).getWord());
        assertEquals("description value.", ((VerbatimBlock) blocks.get(1).getChildren().get(0)).getProtectedString());
    }

    @Test
    void executeTemplateWithDescription()
    {
        List<Block> blocks = this.errorGenerator.generateErrorBlocks("message.", "description.", false);

        assertEquals(2, blocks.size());
        assertEquals("message. Click on this message for details.",
            ((WordBlock) blocks.get(0).getChildren().get(0)).getWord());
        assertEquals("description.", ((VerbatimBlock) blocks.get(1).getChildren().get(0)).getProtectedString());
    }

    @Test
    void executeTemplateWithThrowable()
    {
        List<Block> blocks = this.errorGenerator.generateErrorBlocks("message.", new Exception("exception"), false);

        assertEquals(2, blocks.size());
        assertEquals("message. Cause: [exception]. Click on this message for details.",
            ((WordBlock) blocks.get(0).getChildren().get(0)).getWord());
        assertTrue(((VerbatimBlock) blocks.get(1).getChildren().get(0)).getProtectedString()
            .contains("java.lang.Exception: exception"));
    }

    @Test
    void templateResultWithAMacroFallsBackToThePlainMessage() throws Exception
    {
        Template template = mock();
        when(this.templateManager.getTemplate(ErrorBlockGenerator.CLASS_ATTRIBUTE_MESSAGE_VALUE + "/default.vm"))
            .thenReturn(template);
        // A rendering limit stopped the transformation of the template, so its result still contains the macros the
        // template is made of. They would be executed outside the reserve scope by the transformation this message is
        // inserted into, so they would fail again and get an error message in the place of this one.
        XDOM xdom = new XDOM(List.of(new GroupBlock(List.of(new MacroBlock("box", Map.of(), false)))));
        when(this.templateManager.execute(template, false)).thenReturn(xdom);

        List<Block> blocks = this.errorGenerator.generateErrorBlocks("message.", "description.", false);

        assertEquals(2, blocks.size());
        assertEquals("message. Click on this message for details.",
            ((WordBlock) blocks.get(0).getChildren().get(0)).getWord());
        assertEquals("description.", ((VerbatimBlock) blocks.get(1).getChildren().get(0)).getProtectedString());
    }

    @Test
    void limitFailureOfTheTemplateIsNotLoggedAsAnError() throws Exception
    {
        Template template = mock();
        when(this.templateManager.getTemplate(ErrorBlockGenerator.CLASS_ATTRIBUTE_MESSAGE_VALUE + "/default.vm"))
            .thenReturn(template);
        when(this.templateManager.execute(template, false)).thenThrow(new RenderingException("Failed to render",
            new MacroRenderingLimitExceededException(RenderingLimitType.DOCUMENT_SIZE, "The content doesn't fit.")));

        List<Block> blocks = this.errorGenerator.generateErrorBlocks("message.", "description.", false);

        // The plain message is used instead. Nothing is logged as an error: the reserve for the error messages of the
        // rendering being used up is expected, and the exhausted limit itself has been reported already.
        assertEquals(2, blocks.size());
        assertEquals("message. Click on this message for details.",
            ((WordBlock) blocks.get(0).getChildren().get(0)).getWord());
        assertEquals(0, this.logCapture.size());
    }

    @Test
    void otherFailureOfTheTemplateIsLoggedAsAnError() throws Exception
    {
        Template template = mock();
        when(this.templateManager.getTemplate(ErrorBlockGenerator.CLASS_ATTRIBUTE_MESSAGE_VALUE + "/default.vm"))
            .thenReturn(template);
        when(this.templateManager.execute(template, false)).thenThrow(new RenderingException("Failed to render"));

        List<Block> blocks = this.errorGenerator.generateErrorBlocks("message.", "description.", false);

        assertEquals(2, blocks.size());
        assertEquals("Failed to generate error rendering message", this.logCapture.getMessage(0));
    }

    @Test
    void executeTemplateInRestrictedContext() throws Exception
    {
        Template template = mock(Template.class);
        when(this.templateManager.getTemplate(ErrorBlockGenerator.CLASS_ATTRIBUTE_MESSAGE_VALUE + "/default.vm"))
            .thenReturn(template);
        XDOM xdom = new XDOM(Arrays.asList(new WordBlock("result")));
        when(this.templateManager.execute(template, false)).thenReturn(xdom);

        when(this.renderingContext.isRestricted()).thenReturn(true);

        List<Block> blocks = this.errorGenerator.generateErrorBlocks("message.", "description.", false);

        verify(this.renderingContext).push(any(), any(), any(), any(), eq(false), any());

        assertSame(xdom.getChildren(), blocks);
    }
}
