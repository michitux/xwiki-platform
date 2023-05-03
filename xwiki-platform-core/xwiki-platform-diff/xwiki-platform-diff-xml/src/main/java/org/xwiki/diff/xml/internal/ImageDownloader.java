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

import java.io.IOException;
import java.net.URI;
import java.util.Date;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.Cookie;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.xwiki.component.annotation.Component;
import org.xwiki.diff.xml.XMLDiffConfiguration;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;

/**
 * Component for downloading images from a URL with the given cookies.
 *
 * @since 14.10.10
 * @since 15.4RC1
 * @version $Id$
 */
@Component(roles = ImageDownloader.class)
@Singleton
public class ImageDownloader
{
    @Inject
    private HttpClientBuilderFactory httpClientBuilderFactory;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private XMLDiffConfiguration configuration;

    /**
     * The result of a download request.
     */
    public static class DownloadResult
    {
        private final byte[] data;

        private final String mimeType;

        /**
         * @param data the downloaded data
         * @param mimeType the MIME type of the downloaded data
         */
        public DownloadResult(byte[] data, String mimeType)
        {
            this.data = data;
            this.mimeType = mimeType;
        }

        /**
         * @return the downloaded data
         */
        public byte[] getData()
        {
            return this.data;
        }

        /**
         * @return the MIME type of the downloaded data
         */
        public String getMimeType()
        {
            return this.mimeType;
        }
    }

    /**
     * Download the image from the given URL with the cookies from the current request.
     *
     * @param uri the URL of the image
     * @return the image as a byte array
     * @throws IOException if there was an error downloading the image
     */
    public DownloadResult download(URI uri) throws IOException
    {
        HttpClientBuilder httpClientBuilder = configureHttpClientBuilder();

        HttpGet getMethod = initializeGetMethod(uri);

        try (CloseableHttpClient httpClient = httpClientBuilder.build();
             CloseableHttpResponse response = httpClient.execute(getMethod))
        {
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
                HttpEntity entity = response.getEntity();
                // Remove the content type parameters, such as the charset, so they don't influence the diff.
                String contentType = entity.getContentType() != null ? entity.getContentType().getValue() : null;
                contentType = StringUtils.substringBefore(contentType, ";");

                if (!StringUtils.startsWith(contentType, "image/")) {
                    throw new IOException(String.format("The content of [%s] is not an image.", uri));
                }

                long maximumSize = this.configuration.getMaximumDataURISize();
                if (maximumSize > 0 && entity.getContentLength() > maximumSize) {
                    throw new IOException(String.format("The content length of [%s] is too big.", uri));
                }

                byte[] content;
                if (maximumSize > 0) {
                    try (BoundedInputStream boundedInputStream = new BoundedInputStream(entity.getContent(),
                        maximumSize))
                    {
                        content = IOUtils.toByteArray(boundedInputStream);
                    }

                    if (content.length == maximumSize) {
                        throw new IOException(String.format("The content of [%s] is too big.", uri));
                    }
                } else {
                    content = IOUtils.toByteArray(entity.getContent());
                }

                return new DownloadResult(content, contentType);
            } else {
                throw new IOException(statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
            }
        }
    }

    private HttpGet initializeGetMethod(URI uri)
    {
        HttpGet getMethod = new HttpGet(uri);
        int timeoutMilliseconds = this.configuration.getHttpTimeout() * 1000;
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(timeoutMilliseconds)
            .setSocketTimeout(timeoutMilliseconds)
            .setConnectionRequestTimeout(timeoutMilliseconds)
            .build();
        getMethod.setConfig(requestConfig);
        return getMethod;
    }

    private HttpClientBuilder configureHttpClientBuilder()
    {
        HttpClientBuilder httpClientBuilder = this.httpClientBuilderFactory.create();
        httpClientBuilder.useSystemProperties();
        httpClientBuilder.setUserAgent("XWikiHTMLDiff");
        XWikiRequest request = this.xcontextProvider.get().getRequest();
        if (request != null) {
            // Copy the cookies from the current request. Let the HTTP client take care of matching cookies against
            // the request URI.
            BasicCookieStore cookieStore = new BasicCookieStore();
            for (Cookie cookie : request.getCookies()) {
                cookieStore.addCookie(convertCookie(cookie));
            }

            httpClientBuilder.setDefaultCookieStore(cookieStore);
        }
        return httpClientBuilder;
    }

    private static BasicClientCookie convertCookie(Cookie cookie)
    {
        BasicClientCookie result = new BasicClientCookie(cookie.getName(), cookie.getValue());
        if (cookie.getMaxAge() > -1) {
            Date expires = new Date(System.currentTimeMillis() + cookie.getMaxAge() * 1000L);
            result.setExpiryDate(expires);
        }
        result.setDomain(cookie.getDomain());
        result.setPath(cookie.getPath());
        result.setSecure(cookie.getSecure());
        if (cookie.isHttpOnly()) {
            result.setAttribute("httponly", "true");
        }
        result.setComment(cookie.getComment());

        return result;
    }
}
