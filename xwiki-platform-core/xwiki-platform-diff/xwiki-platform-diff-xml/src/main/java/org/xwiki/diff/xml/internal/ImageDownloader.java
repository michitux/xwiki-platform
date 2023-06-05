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
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.xwiki.component.annotation.Component;
import org.xwiki.diff.xml.XMLDiffDataURIConverterConfiguration;
import org.xwiki.security.authentication.AuthenticationConfiguration;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;

/**
 * Component for downloading images from a URL with the given cookies.
 *
 * @since 14.10.11
 * @since 15.4RC1
 * @version $Id$
 */
@Component(roles = ImageDownloader.class)
@Singleton
public class ImageDownloader
{
    private static final String COOKIE_DOMAIN_PREFIX = ".";

    private static final String HEADER_COOKIE = "Cookie";

    @Inject
    private HttpClientBuilderFactory httpClientBuilderFactory;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private XMLDiffDataURIConverterConfiguration configuration;

    @Inject
    private AuthenticationConfiguration authenticationConfiguration;

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

                long maximumSize = this.configuration.getMaximumContentSize();
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
        int timeoutMilliseconds = this.configuration.getHTTPTimeout() * 1000;
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(timeoutMilliseconds)
            .setSocketTimeout(timeoutMilliseconds)
            .setConnectionRequestTimeout(timeoutMilliseconds)
            .build();
        getMethod.setConfig(requestConfig);
        XWikiRequest request = this.xcontextProvider.get().getRequest();
        if (request != null && matchesCookieDomain(uri.getHost(), request)) {
            // Copy the cookie header from the current request.
            getMethod.setHeader(HEADER_COOKIE, request.getHeader(HEADER_COOKIE));
        }

        return getMethod;
    }

    private HttpClientBuilder configureHttpClientBuilder()
    {
        HttpClientBuilder httpClientBuilder = this.httpClientBuilderFactory.create();
        httpClientBuilder.useSystemProperties();
        httpClientBuilder.setUserAgent("XWikiHTMLDiff");
        return httpClientBuilder;
    }

    /**
     * @return if the host matches the cookie domain of the current request
     */
    private boolean matchesCookieDomain(String host, HttpServletRequest request)
    {
        String serverName = request.getServerName();
        // Add a leading dot to avoid matching domains that are longer versions of the cookie domain and to ensure
        // that the cookie domain itself is matched as the cookie domain also contains the leading dot. Always add
        // the dot as two dots will still match.
        String prefixedServerName = COOKIE_DOMAIN_PREFIX + serverName;

        Optional<String> cookieDomain =
            this.authenticationConfiguration.getCookieDomains().stream()
                .filter(prefixedServerName::endsWith)
                .findFirst();

        // If there is a cookie domain, check if the host also matches it.
        return cookieDomain.map((COOKIE_DOMAIN_PREFIX + host)::endsWith)
            // If no cookie domain is configured, check for an exact match with the server name as no domain is sent in
            // this case and thus the cookie isn't valid for subdomains.
            .orElseGet(() -> host.equals(serverName));
    }

}
