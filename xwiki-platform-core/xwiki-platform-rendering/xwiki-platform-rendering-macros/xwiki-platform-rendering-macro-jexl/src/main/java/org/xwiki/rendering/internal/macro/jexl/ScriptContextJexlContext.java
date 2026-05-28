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

import javax.script.ScriptContext;

import org.apache.commons.jexl3.JexlContext;

/**
 * Adapts a {@link ScriptContext} to a JEXL context without copying the bindings.
 *
 * @version $Id$
 * @since 18.4.0
 */
class ScriptContextJexlContext implements JexlContext
{
    private final ScriptContext scriptContext;

    ScriptContextJexlContext(ScriptContext scriptContext)
    {
        this.scriptContext = scriptContext;
    }

    @Override
    public Object get(String name)
    {
        return this.scriptContext.getAttribute(name);
    }

    @Override
    public boolean has(String name)
    {
        return this.scriptContext.getAttributesScope(name) >= 0;
    }

    @Override
    public void set(String name, Object value)
    {
        int scope = this.scriptContext.getAttributesScope(name);
        if (scope < 0) {
            scope = ScriptContext.ENGINE_SCOPE;
        }

        this.scriptContext.setAttribute(name, value, scope);
    }
}
