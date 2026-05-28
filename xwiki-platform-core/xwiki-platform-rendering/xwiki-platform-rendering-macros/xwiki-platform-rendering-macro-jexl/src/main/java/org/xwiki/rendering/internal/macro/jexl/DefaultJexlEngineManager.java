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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.inject.Singleton;
import javax.script.ScriptContext;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.JexlScript;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.rendering.macro.jexl.JexlEngineManager;

/**
 * Default {@link JexlEngineManager} implementation.
 *
 * @version $Id$
 * @since 18.4.0
 */
@Component
@Singleton
public class DefaultJexlEngineManager implements JexlEngineManager, Initializable
{
    static final Map<String, Object> STATIC_NAMESPACES = Map.of(
        "arrays", Arrays.class,
        "collections", Collections.class,
        "math", Math.class);

    private static final Set<Class<?>> CONSTRUCTOR_ALLOWLIST = Set.of(
        ArrayDeque.class,
        ArrayList.class,
        HashMap.class,
        HashSet.class,
        LinkedHashMap.class,
        LinkedHashSet.class,
        LinkedList.class,
        TreeMap.class,
        TreeSet.class);

    private JexlEngine engine;

    @Override
    public void initialize() throws InitializationException
    {
        this.engine = new JexlBuilder()
            .cache(512)
            .strict(true)
            .safe(false)
            .namespaces(STATIC_NAMESPACES)
            .permissions(new VelocityLikeJexlPermissions(CONSTRUCTOR_ALLOWLIST, Set.copyOf(STATIC_NAMESPACES.values())))
            .create();
    }

    @Override
    public JexlScript createScript(String source)
    {
        return this.engine.createScript(source);
    }

    @Override
    public JexlExpression createExpression(String source)
    {
        return this.engine.createExpression(source);
    }

    @Override
    public JexlContext createContext(ScriptContext scriptContext)
    {
        return new ScriptContextJexlContext(scriptContext);
    }

    @Override
    public Object evaluate(JexlScript script, JexlContext context)
    {
        return script.execute(context);
    }

    @Override
    public Object evaluate(JexlExpression expression, JexlContext context)
    {
        return expression.evaluate(context);
    }
}
