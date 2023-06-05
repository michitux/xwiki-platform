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
import java.net.MalformedURLException;
import java.net.URL;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.xwiki.diff.DiffException;
import org.xwiki.diff.xml.XMLDiffDataURIConverterConfiguration;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.security.authorization.AccessDeniedException;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.DocumentRevisionProvider;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.XWikiPluginManager;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.web.XWikiServletRequestStub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AttachmentDataURIConverter}.
 *
 * @version $Id$
 */
@OldcoreTest
class AttachmentDataURIConverterTest
{
    private static final String ATTACHMENT_NAME = "attachment.png";

    private static final String DATA_URI = "data:image/png;base64,YXR0YWNobWVudENvbnRlbnQ=";

    private static final DocumentReference DOCUMENT_REFERENCE = new DocumentReference("xwiki", "Space", "Page");

    private static final AttachmentReference ATTACHMENT_REFERENCE =
        new AttachmentReference(ATTACHMENT_NAME, DOCUMENT_REFERENCE);

    private static final String ATTACHMENT_URL = "/xwiki/bin/download/Space/Page/attachment.png";

    private static final String BASE_URL = "https://example.com";

    private static final String REQUEST_URL = BASE_URL + "/xwiki/bin/view/Space/Page";

    @MockComponent
    @Named("resource/standardURL")
    private EntityReferenceResolver<String> resolver;

    @MockComponent
    private DocumentRevisionProvider documentRevisionProvider;

    @MockComponent
    private XMLDiffDataURIConverterConfiguration configuration;

    @InjectMockComponents
    private AttachmentDataURIConverter converter;

    @InjectMockitoOldcore
    private MockitoOldcore mockitoOldcore;

    @Mock
    private XWikiPluginManager mockPluginManager;

    @BeforeEach
    void configureBaseURL() throws MalformedURLException
    {
        XWikiContext context = this.mockitoOldcore.getXWikiContext();
        XWikiServletRequestStub request =
            new XWikiServletRequestStub.Builder().setRequestURL(new URL(REQUEST_URL)).build();
        context.setRequest(request);
    }

    @BeforeEach
    void mockPluginManager()
    {
        when(this.mockPluginManager.downloadAttachment(any(XWikiAttachment.class), any()))
            .then(invocation -> invocation.getArgument(0));
        when(this.mockitoOldcore.getSpyXWiki().getPluginManager()).thenReturn(this.mockPluginManager);

    }

    @Test
    void dataURI() throws DiffException
    {
        String dataURL = "data:test";
        assertEquals(dataURL, this.converter.convert(dataURL));
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 200 })
    void simpleConversion(long sizeLimit) throws DiffException, IOException, XWikiException, AccessDeniedException
    {
        setUpDocument(ATTACHMENT_URL);
        when(this.configuration.getMaximumContentSize()).thenReturn(sizeLimit);

        assertEquals(DATA_URI, this.converter.convert(ATTACHMENT_URL));
        verify(this.mockitoOldcore.getMockContextualAuthorizationManager()).checkAccess(Right.VIEW, DOCUMENT_REFERENCE);
    }

    @Test
    void conversionWithRevision() throws DiffException, IOException, XWikiException
    {
        String revision = "1.1";
        String url = ATTACHMENT_URL + "?rev=" + revision;

        // Work with a mock attachment as the real attachment doesn't seem to support versioning in MockitoOldcore.
        XWikiDocument document = setUpDocument(url);
        XWikiAttachment mockAttachment = mock();
        when(mockAttachment.getContentInputStream(any()))
            .thenReturn(new ByteArrayInputStream("currentContent".getBytes()));
        when(mockAttachment.getFilename()).thenReturn(ATTACHMENT_NAME);
        when(mockAttachment.getMimeType(any())).thenReturn("image/png");

        XWikiAttachment mockOldAttachment = mock();
        when(mockOldAttachment.getContentInputStream(any()))
            .thenReturn(new ByteArrayInputStream("attachmentContent".getBytes()));
        when(mockOldAttachment.getFilename()).thenReturn(ATTACHMENT_NAME);
        when(mockOldAttachment.getMimeType(any())).thenReturn("image/png");
        when(mockAttachment.getAttachmentRevision(eq(revision), any())).thenReturn(mockOldAttachment);

        document.setAttachment(mockAttachment);
        // Mock the call to get the document as we cannot save the document with the mock attachment as cloning the
        // mock attachment fails.
        doReturn(document).when(this.mockitoOldcore.getSpyXWiki()).getDocument(eq(DOCUMENT_REFERENCE), any());

        assertEquals(DATA_URI, this.converter.convert(url));
    }

    @Test
    void conversionWithResizing() throws DiffException, IOException, XWikiException
    {
        String url = ATTACHMENT_URL + "?width=200&height=100";

        setUpDocument(url);

        XWikiAttachment resizedImage = mock();
        when(resizedImage.getMimeType(any())).thenReturn("image/png");
        when(resizedImage.getContentInputStream(any()))
            .thenReturn(new ByteArrayInputStream("resizedContent".getBytes()));

        when(this.mockPluginManager.downloadAttachment(any(XWikiAttachment.class), any())).then(invocation -> {
            XWikiAttachment attachment = invocation.getArgument(0);
            XWikiContext context = invocation.getArgument(1);

            // Verify that the context has the correct parameters. This cannot be done with an argument captor
            // because the context is reset after the invocation.
            assertEquals("200", context.getRequest().getParameter("width"));
            assertEquals("100", context.getRequest().getParameter("height"));

            assertEquals(ATTACHMENT_NAME, attachment.getFilename());

            return resizedImage;
        });

        assertEquals("data:image/png;base64,cmVzaXplZENvbnRlbnQ=", this.converter.convert(url));
    }

    @Test
    void tooLargeImageFails() throws XWikiException, IOException
    {
        XWikiDocument document = setUpDocument(ATTACHMENT_URL);

        XWikiAttachment attachment = document.getAttachment(ATTACHMENT_NAME);
        attachment.setContent(new ByteArrayInputStream(new byte[1024 * 1024 + 1]));
        this.mockitoOldcore.getSpyXWiki().saveDocument(document, this.mockitoOldcore.getXWikiContext());
        when(this.configuration.getMaximumContentSize()).thenReturn(1024L * 1024L);

        DiffException exception = assertThrows(DiffException.class, () -> this.converter.convert(ATTACHMENT_URL));
        assertEquals(String.format("The attachment [%s] is too big.", ATTACHMENT_REFERENCE), exception.getMessage());
    }

    private XWikiDocument setUpDocument(String url) throws IOException, XWikiException
    {
        when(this.resolver.resolve(BASE_URL + url, EntityType.ATTACHMENT)).thenReturn(ATTACHMENT_REFERENCE);

        XWikiDocument document = new XWikiDocument(DOCUMENT_REFERENCE);
        document.setAttachment(ATTACHMENT_NAME, new ByteArrayInputStream("attachmentContent".getBytes()),
            this.mockitoOldcore.getXWikiContext());
        this.mockitoOldcore.getSpyXWiki().saveDocument(document, this.mockitoOldcore.getXWikiContext());

        return document;
    }
}
