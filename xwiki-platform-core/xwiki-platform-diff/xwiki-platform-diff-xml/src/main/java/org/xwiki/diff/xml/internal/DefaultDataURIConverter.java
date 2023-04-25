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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;
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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.cache.eviction.LRUEvictionConfiguration;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.diff.DiffException;
import org.xwiki.url.URLSecurityManager;
import org.xwiki.user.CurrentUserReference;
import org.xwiki.user.UserReferenceSerializer;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;

/**
 * Default implementation of {@link DataURIConverter}.
 * 
 * @version $Id$
 * @since 11.10.1
 * @since 12.0RC1
 */
@Component
@Singleton
public class DefaultDataURIConverter implements DataURIConverter, Initializable, Disposable
{
    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private CacheManager cacheManager;

    @Inject
    private URLSecurityManager urlSecurityManager;

    @Inject
    private UserReferenceSerializer<String> userReferenceSerializer;

    private Cache<String> cache;

    private Cache<DiffException> failureCache;

    @Override
    public void initialize() throws InitializationException
    {
        CacheConfiguration cacheConfig = new CacheConfiguration();
        cacheConfig.setConfigurationId("diff.html.dataURI");
        LRUEvictionConfiguration lru = new LRUEvictionConfiguration();
        lru.setMaxEntries(100);
        cacheConfig.put(LRUEvictionConfiguration.CONFIGURATIONID, lru);

        CacheConfiguration failureCacheConfiguration = new CacheConfiguration();
        failureCacheConfiguration.setConfigurationId("diff.html.failureCache");
        LRUEvictionConfiguration failureLRU = new LRUEvictionConfiguration();
        failureLRU.setMaxEntries(1000);
        // Cache failures for an hour. This is to avoid hammering the server with requests for images that don't
        // exist or are inaccessible or too large.
        failureLRU.setLifespan(3600);
        failureCacheConfiguration.put(LRUEvictionConfiguration.CONFIGURATIONID, failureLRU);

        try {
            this.cache = this.cacheManager.createNewCache(cacheConfig);
            this.failureCache = this.cacheManager.createNewCache(failureCacheConfiguration);
        } catch (Exception e) {
            // Dispose the cache if it has been created.
            if (this.cache != null) {
                this.cache.dispose();
            }
            throw new InitializationException("Failed to create the Data URI cache.", e);
        }
    }

    @Override
    public void dispose()
    {
        this.cache.dispose();
        this.failureCache.dispose();
    }

    /**
     * Compute a cache key based on the current user and the URL.
     *
     * @param url the url
     * @return the cache key
     */
    private String getCacheKey(URL url)
    {
        String userPart = this.userReferenceSerializer.serialize(CurrentUserReference.INSTANCE);
        return String.format("%d:%s:%s", userPart.length(), userPart, url.toString());
    }

    @Override
    public String convert(String url) throws DiffException
    {
        if (url.startsWith("data:")) {
            // Already data URI.
            return url;
        }

        // Convert URL to absolute URL to avoid issues with relative URLs that might reference different images
        // in different subwikis.
        URL absoluteURL = null;
        try {
            absoluteURL = getAbsoluteURL(url);
        } catch (MalformedURLException e) {
            throw new DiffException("Failed to convert malformed url [" + url + "] to absolute URL.", e);
        }

        String cacheKey = getCacheKey(absoluteURL);

        try {
            String dataURI = this.cache.get(cacheKey);

            if (dataURI == null) {
                DiffException failure = this.failureCache.get(cacheKey);

                if (failure != null) {
                    throw failure;
                }

                dataURI = convert(absoluteURL);
                this.cache.set(cacheKey, dataURI);
            }

            return dataURI;
        } catch (IOException | URISyntaxException e) {
            DiffException diffException = new DiffException("Failed to convert [" + url + "] to data URI.", e);
            this.failureCache.set(cacheKey, diffException);
            throw diffException;
        }
    }

    private URL getAbsoluteURL(String relativeURL) throws MalformedURLException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        URL baseURL = xcontext.getURLFactory().getServerURL(xcontext);
        return new URL(baseURL, relativeURL);
    }

    private String convert(URL url) throws IOException, URISyntaxException
    {
        if (!this.urlSecurityManager.isDomainTrusted(url)) {
            throw new IOException(String.format("The URL [%s] is not trusted.", url));
        }

        HttpEntity entity = fetch(url.toURI());
        // Remove the content type parameters, such as the charset, so they don't influence the diff.
        String contentType = StringUtils.substringBefore(entity.getContentType().getValue(), ";");

        if (!StringUtils.startsWith(contentType, "image/")) {
            throw new IOException(String.format("The content of [%s] is not an image.", url));
        }

        // TODO: make this configurable.
        int maximumSize = 1024 * 1024;
        BoundedInputStream boundedInputStream = new BoundedInputStream(entity.getContent(), maximumSize);
        byte[] content = IOUtils.toByteArray(boundedInputStream);
        if (content.length == maximumSize) {
            throw new IOException(String.format("The content of [%s] is too big.", url));
        }

        return String.format("data:%s;base64,%s", contentType, Base64.getEncoder().encodeToString(content));
    }

    private HttpEntity fetch(URI uri) throws IOException
    {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
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

        CloseableHttpClient httpClient = httpClientBuilder.build();
        HttpGet getMethod = new HttpGet(uri);

        CloseableHttpResponse response = httpClient.execute(getMethod);
        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
            return response.getEntity();
        } else {
            throw new IOException(statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
        }
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
