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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;

import org.xwiki.diff.DiffException;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

/**
 * Abstract base class for {@link DataURIConverter} implementations providing common helper methods.
 *
 * @version $Id$
 * @since 14.10.13
 * @since 15.5RC1
 */
public abstract class AbstractDataURIConverter implements DataURIConverter
{
    /**
     * Convert the given URL to an absolute URL using the request URL from the given context.
     *
     * @param url the URL to convert
     * @param xcontext the XWiki context
     * @return the absolute URL
     * @throws DiffException if the URL cannot be converted due to being malformed
     */
    protected URL getAbsoluteURL(String url, XWikiContext xcontext) throws DiffException
    {
        URL absoluteURL;
        try {
            if (xcontext.getRequest() != null) {
                URL requestURL = XWiki.getRequestURL(xcontext.getRequest());
                absoluteURL = new URL(requestURL, url);
            } else {
                absoluteURL = new URL(url);
            }
        } catch (MalformedURLException | XWikiException e) {
            throw new DiffException(String.format("Failed to resolve [%s] to an absolute URL.", url), e);
        }
        return absoluteURL;
    }

    /**
     * Get a data URI for the given content and content type.
     *
     * @param contentType the content type
     * @param content the content
     * @return the data URI
     */
    protected static String getDataURI(String contentType, byte[] content)
    {
        return String.format("data:%s;base64,%s", contentType, Base64.getEncoder().encodeToString(content));
    }
}
