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

import java.util.Collections;
import java.util.List;

import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.script.ScriptMacroParameters;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.script.ScriptContextManager;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JexlTemplateMacro}.
 */
@ComponentTest
@ComponentList(DefaultJexlEngineManager.class)
class JexlTemplateMacroTest
{
    @MockComponent
    private ScriptContextManager scriptContextManager;

    @MockComponent
    private MacroContentParser contentParser;

    @InjectMockComponents
    private JexlTemplateMacro macro;

    private SimpleScriptContext scriptContext;

    @BeforeEach
    void setUp()
    {
        this.scriptContext = new SimpleScriptContext();
        this.scriptContext.setAttribute("shared", "global", ScriptContext.ENGINE_SCOPE);
        when(this.scriptContextManager.getScriptContext()).thenReturn(this.scriptContext);
    }

    @Test
    void scriptValuesAreNestedUnderTemplateContext() throws Exception
    {
        List<Block> result = this.macro.execute(new ScriptMacroParameters(),
            "<script>let shared = 'local'; {'value': shared}</script><p v-text=\"template.value + '-' + shared\" />",
            new MacroTransformationContext());

        ParagraphBlock paragraphBlock = assertInstanceOf(ParagraphBlock.class, result.get(0));
        assertEquals(List.of(new WordBlock("local-global")), paragraphBlock.getChildren());
        assertEquals("global", this.scriptContext.getAttribute("shared"));
    }

    @Test
    void xwikiMacroTagsProduceMacroBlocks() throws Exception
    {
        List<Block> result = this.macro.execute(new ScriptMacroParameters(),
            "<xw-macro-warning>Watch out!</xw-macro-warning>", new MacroTransformationContext());

        MacroBlock macroBlock = assertInstanceOf(MacroBlock.class, result.get(0));
        assertEquals("warning", macroBlock.getId());
        assertEquals(Collections.emptyMap(), macroBlock.getParameters());
        assertEquals("Watch out!", macroBlock.getContent());
    }
}
