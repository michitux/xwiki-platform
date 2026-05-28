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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;

import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlScript;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.MacroPreparationException;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.macro.jexl.JexlEngineManager;
import org.xwiki.rendering.macro.script.AbstractScriptMacro;
import org.xwiki.rendering.macro.script.ScriptMacroParameters;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.script.ScriptContextManager;

/**
 * Executes an Apache Commons JEXL script.
 *
 * @version $Id$
 * @since 18.4.0
 */
@Component
@Named("jexl")
@Singleton
public class JexlMacro extends AbstractScriptMacro<ScriptMacroParameters>
{
    public static final String MACRO_ATTRIBUTE = "jexl.script";

    private static final String DESCRIPTION = "Executes an Apache Commons JEXL script.";

    private static final String JEXL_CONTENT_DESCRIPTION = "the JEXL script to execute";

    @Inject
    private JexlEngineManager jexlEngineManager;

    @Inject
    private ScriptContextManager scriptContextManager;

    public JexlMacro()
    {
        super("JEXL", DESCRIPTION, new DefaultContentDescriptor(JEXL_CONTENT_DESCRIPTION), ScriptMacroParameters.class);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return true;
    }

    @Override
    protected String evaluateString(ScriptMacroParameters parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        try {
            JexlScript script = getScript(content, context);
            ScriptContext scriptContext = this.scriptContextManager.getScriptContext();
            if (scriptContext == null) {
                scriptContext = new SimpleScriptContext();
            }
            JexlContext jexlContext = this.jexlEngineManager.createContext(scriptContext);
            Object result = this.jexlEngineManager.evaluate(script, jexlContext);
            return result == null ? "" : String.valueOf(result);
        } catch (JexlException e) {
            throw new MacroExecutionException("Failed to evaluate JEXL Macro for content [" + content + ']'
                , e);
        }
    }

    @Override
    public void prepare(MacroBlock macroBlock) throws MacroPreparationException
    {
        try {
            macroBlock.setAttribute(MACRO_ATTRIBUTE, this.jexlEngineManager.createScript(macroBlock.getContent()));
        } catch (JexlException e) {
            throw new MacroPreparationException("Failed to compile the JEXL script", e);
        }
    }

    private JexlScript getScript(String content, MacroTransformationContext context)
    {
        MacroBlock currentMacroBlock = context.getCurrentMacroBlock();
        if (currentMacroBlock != null) {
            Object preparedScript = currentMacroBlock.getAttribute(MACRO_ATTRIBUTE);
            if (preparedScript instanceof JexlScript) {
                return (JexlScript) preparedScript;
            }
        }

        return this.jexlEngineManager.createScript(content);
    }
}
