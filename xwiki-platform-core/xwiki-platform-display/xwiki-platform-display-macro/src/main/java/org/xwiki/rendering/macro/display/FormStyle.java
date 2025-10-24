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
package org.xwiki.rendering.macro.display;

import org.xwiki.stability.Unstable;

/**
 * The style used to display the form fields.
 *
 * @version $Id$
 * @since 17.9.0RC1
 */
@Unstable
public enum FormStyle
{
    /**
     * Display the form fields using XWiki's vertical form style.
     */
    XFORM,
    /**
     * Display the form fields using a horizontal form style.
     */
    HORIZONTAL,
    /**
     * Display the form fields using XWiki's inline form style, everything on a single line if possible.
     */
    INLINE
}
