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

import java.util.List;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;

import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlScript;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.MacroPreparationException;
import org.xwiki.rendering.macro.jexl.JexlEngineManager;
import org.xwiki.rendering.macro.script.ScriptMacroParameters;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.script.ScriptContextManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JexlMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class JexlMacroTest
{
    @MockComponent
    private JexlEngineManager jexlEngineManager;

    @MockComponent
    private ScriptContextManager scriptContextManager;

    @MockComponent
    private MacroContentParser contentParser;

    @InjectMockComponents
    private JexlMacro macro;

    @Test
    void evaluatePreparedScript() throws MacroPreparationException, MacroExecutionException
    {
        MacroBlock block = new MacroBlock("jexl", Map.of(), "'result'", false);
        MacroTransformationContext context = new MacroTransformationContext();
        context.setCurrentMacroBlock(block);

        JexlScript script = mock(JexlScript.class);
        when(this.jexlEngineManager.createScript("'result'"))
            .thenReturn(script);

        SimpleScriptContext scriptContext = new SimpleScriptContext();
        when(this.scriptContextManager.getScriptContext()).thenReturn(scriptContext);
        JexlContext jexlContext = mock(JexlContext.class);
        when(this.jexlEngineManager.createContext(scriptContext)).thenReturn(jexlContext);
        when(this.jexlEngineManager.evaluate(script, jexlContext)).thenReturn("result");

        List<Block> resultBlocks = List.of(new WordBlock("result"));
        when(this.contentParser.parse("result", context, false, false)).thenReturn(new XDOM(resultBlocks));

        this.macro.prepare(block);

        assertSame(script, block.getAttribute(JexlMacro.MACRO_ATTRIBUTE));
        assertEquals(resultBlocks, this.macro.execute(new ScriptMacroParameters(), "'result'", context));
    }

    @Test
    void evaluateUnpreparedScript() throws MacroExecutionException
    {
        MacroTransformationContext context = new MacroTransformationContext();
        ScriptContext scriptContext = new SimpleScriptContext();
        when(this.scriptContextManager.getScriptContext()).thenReturn(scriptContext);

        JexlScript script = mock(JexlScript.class);
        when(this.jexlEngineManager.createScript("42")).thenReturn(script);
        JexlContext jexlContext = mock(JexlContext.class);
        when(this.jexlEngineManager.createContext(scriptContext)).thenReturn(jexlContext);
        when(this.jexlEngineManager.evaluate(script, jexlContext)).thenReturn("42");
        when(this.contentParser.parse("42", context, false, false)).thenReturn(new XDOM(List.of(new WordBlock("42"))));

        assertEquals(List.of(new WordBlock("42")), this.macro.execute(new ScriptMacroParameters(), "42", context));
    }
}
