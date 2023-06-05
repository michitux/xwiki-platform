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
package org.xwiki.diff.xml.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.diff.DiffException;
import org.xwiki.diff.xml.XMLDiffDataURIConverterConfiguration;

/**
 * Default implementation of {@link DataURIConverter}, uses the configured one.
 *
 * @version $Id$
 * @since 11.10.1
 * @since 12.0RC1
 */
@Component
@Singleton
public class DefaultDataURIConverter implements DataURIConverter
{
    @Inject
    @Named("context")
    private ComponentManager componentManager;

    @Inject
    private XMLDiffDataURIConverterConfiguration configuration;

    @Override
    public String convert(String url) throws DiffException
    {
        try {
            DataURIConverter converter =
                this.componentManager.getInstance(DataURIConverter.class, this.configuration.getConverterHint());
            return converter.convert(url);
        } catch (ComponentLookupException e) {
            throw new DiffException(String.format("Failed to find a data URI converter for hint [%s].",
                this.configuration.getConverterHint()), e);
        }
    }
}
