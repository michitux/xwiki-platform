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
import java.net.URISyntaxException;
import java.net.URL;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.cache.eviction.EntryEvictionConfiguration;
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

/**
 * Implementation of {@link DataURIConverter} that uses an HTTP client to embed images.
 *
 * @since 14.10.13
 * @since 15.5RC1
 * @version $Id$
 */
@Component
@Singleton
@Named("http")
public class HTTPDataURIConverter extends AbstractDataURIConverter implements Initializable, Disposable
{
    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private CacheManager cacheManager;

    @Inject
    private URLSecurityManager urlSecurityManager;

    @Inject
    private UserReferenceSerializer<String> userReferenceSerializer;

    @Inject
    private ImageDownloader imageDownloader;

    private Cache<String> cache;

    private Cache<DiffException> failureCache;

    @Override
    public void initialize() throws InitializationException
    {
        CacheConfiguration cacheConfig = new CacheConfiguration();
        cacheConfig.setConfigurationId("diff.html.dataURI");
        LRUEvictionConfiguration lru = new LRUEvictionConfiguration();
        lru.setMaxEntries(100);
        cacheConfig.put(EntryEvictionConfiguration.CONFIGURATIONID, lru);

        CacheConfiguration failureCacheConfiguration = new CacheConfiguration();
        failureCacheConfiguration.setConfigurationId("diff.html.dataURIFailureCache");
        LRUEvictionConfiguration failureLRU = new LRUEvictionConfiguration();
        failureLRU.setMaxEntries(1000);
        // Cache failures for an hour. This is to avoid hammering the server with requests for images that don't
        // exist or are inaccessible or too large.
        failureLRU.setLifespan(3600);
        failureCacheConfiguration.put(EntryEvictionConfiguration.CONFIGURATIONID, failureLRU);

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
        // Prepend the length of the user part to avoid any kind of confusion between user and URL.
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
        URL absoluteURL = getAbsoluteURL(url, this.xcontextProvider.get());

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

    private String convert(URL url) throws IOException, URISyntaxException
    {
        if (!this.urlSecurityManager.isDomainTrusted(url)) {
            throw new IOException(String.format("The URL [%s] is not trusted.", url));
        }

        ImageDownloader.DownloadResult downloadResult = this.imageDownloader.download(url.toURI());

        return getDataURI(downloadResult.getContentType(), downloadResult.getData());
    }
}
