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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

import javax.inject.Provider;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.diff.xml.XMLDiffDataURIConverterConfiguration;
import org.xwiki.security.authentication.AuthenticationConfiguration;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImageDownloader}.
 *
 * @version $Id$
 */
@ComponentTest
class ImageDownloaderTest
{
    private static final ProtocolVersion HTTP_VERSION = new ProtocolVersion("HTTP", 1, 1);

    private static final URI IMAGE_URI = URI.create("https://www.example.com/image.png");

    private static final String IMAGE_CONTENT_TYPE = "image/png";

    @MockComponent
    private HTTPClientBuilderFactory httpClientBuilderFactory;

    @MockComponent
    private Provider<XWikiContext> xwikiContextProvider;

    @MockComponent
    private XMLDiffDataURIConverterConfiguration configuration;

    @MockComponent
    private AuthenticationConfiguration authenticationConfiguration;

    @InjectMockComponents
    private ImageDownloader imageDownloader;

    @Mock
    private HttpClientBuilder httpClientBuilder;

    @Mock
    private CloseableHttpClient httpClient;

    @Mock
    private CloseableHttpResponse httpResponse;

    @Mock
    private XWikiContext xwikiContext;

    @Mock
    private HttpEntity httpEntity;

    @Mock
    private Header contentTypeHeader;

    @BeforeEach
    public void setupMocks() throws IOException
    {
        when(this.httpClientBuilderFactory.create()).thenReturn(this.httpClientBuilder);
        when(this.httpClientBuilder.build()).thenReturn(this.httpClient);
        when(this.httpClient.execute(any())).thenReturn(this.httpResponse);
        when(this.xwikiContextProvider.get()).thenReturn(this.xwikiContext);
        when(this.httpResponse.getEntity()).thenReturn(this.httpEntity);
        StatusLine statusLine = new BasicStatusLine(HTTP_VERSION, HttpStatus.SC_OK, "OK");
        when(this.httpResponse.getStatusLine()).thenReturn(statusLine);
        when(this.httpEntity.getContentType()).thenReturn(this.contentTypeHeader);
        when(this.contentTypeHeader.getValue()).thenReturn(IMAGE_CONTENT_TYPE);
    }

    @Test
    void throwsOnNon200Status()
    {
        StatusLine statusLine = new BasicStatusLine(HTTP_VERSION, HttpStatus.SC_NOT_FOUND, "Not Found");
        when(this.httpResponse.getStatusLine()).thenReturn(statusLine);
        IOException ioException = assertThrows(IOException.class, () -> this.imageDownloader.download(IMAGE_URI));
        assertEquals("404 Not Found", ioException.getMessage());
    }

    @Test
    void throwsWhenNonImageContentType()
    {
        when(this.contentTypeHeader.getValue()).thenReturn("text/html");
        IOException ioException = assertThrows(IOException.class, () -> this.imageDownloader.download(IMAGE_URI));
        assertEquals(String.format("The content of [%s] is not an image.", IMAGE_URI), ioException.getMessage());
    }

    @Test
    void throwsWhenContentTypeHeaderMissing()
    {
        when(this.httpEntity.getContentType()).thenReturn(null);
        IOException ioException = assertThrows(IOException.class, () -> this.imageDownloader.download(IMAGE_URI));
        assertEquals(String.format("The content of [%s] is not an image.", IMAGE_URI), ioException.getMessage());
    }

    @Test
    void throwsWhenContentLengthTooBig()
    {
        when(this.httpEntity.getContentLength()).thenReturn(1000000000L);
        when(this.configuration.getMaximumContentSize()).thenReturn(100L);
        IOException ioException = assertThrows(IOException.class, () -> this.imageDownloader.download(IMAGE_URI));
        assertEquals(String.format("The content length of [%s] is too big.", IMAGE_URI), ioException.getMessage());
    }

    @Test
    void throwsWhenContentLengthUnknownAndTooBig() throws IOException
    {
        when(this.httpEntity.getContentLength()).thenReturn(-1L);
        InputStream inputStream = new InputStream()
        {
            @Override
            public int read()
            {
                return 1;
            }
        };
        when(this.configuration.getMaximumContentSize()).thenReturn(100L);

        when(this.httpEntity.getContent()).thenReturn(inputStream);
        IOException ioException = assertThrows(IOException.class, () -> this.imageDownloader.download(IMAGE_URI));
        assertEquals(String.format("The content of [%s] is too big.", IMAGE_URI), ioException.getMessage());
    }

    @ParameterizedTest
    @ValueSource(longs = { -1, 1, 100, 200 })
    void returnsContent(long contentLength) throws IOException
    {
        // Set different content lengths to test the different code paths.
        when(this.httpEntity.getContentLength()).thenReturn(contentLength);
        if (contentLength < 200) {
            when(this.configuration.getMaximumContentSize()).thenReturn(200L);
        } else {
            // Test unlimited size.
            when(this.configuration.getMaximumContentSize()).thenReturn(0L);
        }
        byte[] content = new byte[] { 1, 2, 3 };
        when(this.httpEntity.getContent()).thenReturn(new ByteArrayInputStream(content));
        ImageDownloader.DownloadResult result = this.imageDownloader.download(IMAGE_URI);
        assertArrayEquals(content, result.getData());
        assertEquals(IMAGE_CONTENT_TYPE, result.getContentType());
    }

    @ParameterizedTest
    @CsvSource({
        "www.example.com, true, ",
        "www.xwiki.org, false, ",
        "test.example.com, false, ",
        "matches.example.com, true, .example.com"
    })
    void passesCookiesFromRequest(String requestDomain, boolean shouldSendCookie, String cookieDomain)
        throws IOException
    {
        // Set a mock request in the context.
        XWikiRequest request = mock();
        when(request.getServerName()).thenReturn(requestDomain);
        String cookieHeader = "cookie1=value1; cookie2=value2";
        when(request.getHeader("Cookie")).thenReturn(cookieHeader);
        when(this.xwikiContext.getRequest()).thenReturn(request);

        if (StringUtils.isNotBlank(cookieDomain)) {
            when(this.authenticationConfiguration.getCookieDomains()).thenReturn(List.of(cookieDomain));
        }

        // Trigger the download.
        byte[] content = new byte[] { 1, 2, 3 };
        when(this.httpEntity.getContent()).thenReturn(new ByteArrayInputStream(content));
        this.imageDownloader.download(IMAGE_URI);

        ArgumentCaptor<HttpUriRequest> requestCaptor = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(this.httpClient).execute(requestCaptor.capture());

        // Verify that the cookies are passed to the HTTP client if it should do so.
        HttpUriRequest httpRequest = requestCaptor.getValue();
        Header[] headers = httpRequest.getHeaders("Cookie");
        if (shouldSendCookie) {
            assertEquals(1, headers.length);
            assertEquals(cookieHeader, headers[0].getValue());
        } else {
            assertEquals(0, headers.length);
        }
    }
}
