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
package org.xwiki.diff.xml;

import org.xwiki.component.annotation.Role;
import org.xwiki.diff.xml.internal.DataURIConverter;
import org.xwiki.stability.Unstable;

/**
 * Configuration for the XML diff module.
 *
 * @since 14.10.10
 * @since 15.4RC1
 * @version $Id$
 */
@Unstable
@Role
public interface XMLDiffConfiguration
{
    /**
     * @return the timeout to use when fetching data from the web to embed as data URI
     */
    int getHttpTimeout();

    /**
     * @return the maximum size of the data to embed as data URI
     */
    long getMaximumDataURISize();

    /**
     * @return the hint of the {@link DataURIConverter} to use
     */
    String getDataURIConverterHint();
}
