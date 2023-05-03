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
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.diff.xml.XMLDiffConfiguration;

/**
 * Configuration for the XML diff.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultXMLDiffConfiguration implements XMLDiffConfiguration
{
    private static final String PREFIX = "diff.xml";

    @Inject
    @Named("xwikiproperties")
    private ConfigurationSource configurationSource;

    @Override
    public int getHttpTimeout()
    {
        return this.configurationSource.getProperty(getFullKeyName("httpTimeout"), Integer.class, 10);
    }

    @Override
    public long getMaximumDataURISize()
    {
        return this.configurationSource.getProperty(getFullKeyName("maximumDataURISize"), Long.class, 1024L * 1024L);
    }

    @Override
    public String getDataURIConverterHint()
    {
        return this.configurationSource.getProperty(getFullKeyName("dataURIConverterHint"), String.class, "attachment");
    }

    private String getFullKeyName(String shortKeyName)
    {
        return String.format("%s.%s", PREFIX, shortKeyName);
    }
}
