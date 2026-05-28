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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.JexlScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DefaultJexlEngineManager}.
 *
 * @version $Id$
 */
class DefaultJexlEngineManagerTest
{
    private DefaultJexlEngineManager manager;

    @BeforeEach
    void setUp() throws Exception
    {
        this.manager = new DefaultJexlEngineManager();
        this.manager.initialize();
    }

    @Test
    void constructorsAndStaticNamespacesAreAllowlisted()
    {
        SimpleScriptContext scriptContext = new SimpleScriptContext();
        JexlContext context = this.manager.createContext(scriptContext);

        JexlScript constructorScript = this.manager.createScript("#pragma jexl.import java.util\n"
            + "const list = new ArrayList(); list.add('x'); list.size()");
        assertEquals(1, this.manager.evaluate(constructorScript, context));

        JexlExpression expression = this.manager.createExpression("math:abs(-41) + collections:emptyList().size()");
        assertEquals(41, this.manager.evaluate(expression, context));

        JexlScript forbiddenConstructor = this.manager.createScript("#pragma jexl.import java.io\nnew File('.')");
        assertThrows(JexlException.class, () -> this.manager.evaluate(forbiddenConstructor, context));
    }

    @Test
    void dangerousMethodsAreBlockedLikeVelocity()
    {
        SimpleScriptContext scriptContext = new SimpleScriptContext();
        scriptContext.setAttribute("monitor", new Object(), ScriptContext.ENGINE_SCOPE);
        scriptContext.setAttribute("file", new File("."), ScriptContext.ENGINE_SCOPE);
        scriptContext.setAttribute("value", "test", ScriptContext.ENGINE_SCOPE);
        JexlContext context = this.manager.createContext(scriptContext);

        assertEquals(".", this.manager.evaluate(this.manager.createExpression("file.name"), context));
        assertEquals("java.lang.String", this.manager.evaluate(this.manager.createExpression("value.class.name"), context));

        assertThrows(JexlException.class, () -> this.manager.evaluate(this.manager.createExpression("monitor.wait()"), context));
        assertThrows(JexlException.class, () -> this.manager.evaluate(this.manager.createExpression("file.delete()"), context));
        assertThrows(JexlException.class,
            () -> this.manager.evaluate(this.manager.createExpression("value.class.forName('java.util.ArrayList')"),
                context));
    }

    @Test
    void localVariablesAreNotSharedButGlobalBindingsAre()
    {
        SimpleScriptContext scriptContext = new SimpleScriptContext();
        JexlContext context = this.manager.createContext(scriptContext);

        JexlScript script = this.manager.createScript("let localValue = 'local'; sharedValue = 'shared'; sharedValue");
        assertEquals("shared", this.manager.evaluate(script, context));
        assertEquals("shared", scriptContext.getAttribute("sharedValue"));

        assertThrows(JexlException.class,
            () -> this.manager.evaluate(this.manager.createExpression("localValue"), context));
        assertEquals("shared", this.manager.evaluate(this.manager.createExpression("sharedValue"), context));
    }

    @Test
    void lambdasStoredInContextCanBeExecutedFromExpressions()
    {
        SimpleScriptContext scriptContext = new SimpleScriptContext();
        JexlContext context = this.manager.createContext(scriptContext);

        JexlScript script = this.manager.createScript("adder = (left, right) -> left + right");
        this.manager.evaluate(script, context);

        assertEquals(42, this.manager.evaluate(this.manager.createExpression("adder(19, 23)"), context));
    }

    @Test
    void benchmarkManyExpressionsWithSharedContextWithoutCopyingBindings()
    {
        SimpleScriptContext scriptContext = new SimpleScriptContext();
        scriptContext.setBindings(new NoIterationBindings(new HashMap<>(Map.of("base", 40))), ScriptContext.ENGINE_SCOPE);
        JexlContext context = this.manager.createContext(scriptContext);
        JexlExpression expression = this.manager.createExpression("base + offset");

        long start = System.nanoTime();
        for (int i = 0; i < 5000; ++i) {
            scriptContext.setAttribute("offset", 2, ScriptContext.ENGINE_SCOPE);
            assertEquals(42, this.manager.evaluate(expression, context));
        }
        long duration = System.nanoTime() - start;

        assertTrue(duration > 0);
    }

    private static final class NoIterationBindings extends SimpleBindings
    {
        private NoIterationBindings(Map<String, Object> values)
        {
            super(values);
        }

        @Override
        public Set<Entry<String, Object>> entrySet()
        {
            throw new UnsupportedOperationException("entrySet() should not be used when evaluating expressions");
        }
    }
}
