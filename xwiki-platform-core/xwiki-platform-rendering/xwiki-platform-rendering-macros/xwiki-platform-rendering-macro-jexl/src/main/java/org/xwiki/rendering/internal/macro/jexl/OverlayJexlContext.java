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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.jexl3.JexlContext;

/**
 * Local overlay for a parent {@link JexlContext}.
 *
 * @version $Id$
 * @since 18.4.0
 */
class OverlayJexlContext implements JexlContext
{
    private final JexlContext parent;

    private final Map<String, Object> localVariables = new LinkedHashMap<>();

    OverlayJexlContext(JexlContext parent)
    {
        this.parent = parent;
    }

    @Override
    public Object get(String name)
    {
        if (this.localVariables.containsKey(name)) {
            return this.localVariables.get(name);
        }

        return this.parent.get(name);
    }

    @Override
    public boolean has(String name)
    {
        return this.localVariables.containsKey(name) || this.parent.has(name);
    }

    @Override
    public void set(String name, Object value)
    {
        this.localVariables.put(name, value);
    }
}
