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

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.diff.DiffException;
import org.xwiki.diff.xml.XMLDiffConfiguration;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.security.authorization.AccessDeniedException;
import org.xwiki.security.authorization.AuthorizationException;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.CurrentUserReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.DocumentRevisionProvider;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.XWikiPluginInterface;
import com.xpn.xwiki.plugin.XWikiPluginManager;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @MockComponent
    @Named("resource/standardURL")
    private EntityReferenceResolver<String> resolver;

    @MockComponent
    private DocumentRevisionProvider documentRevisionProvider;

    @MockComponent
    private XMLDiffConfiguration configuration;

    @InjectMockComponents
    private AttachmentDataURIConverter converter;

    @InjectMockitoOldcore
    private MockitoOldcore mockitoOldcore;

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
        when(this.configuration.getMaximumDataURISize()).thenReturn(sizeLimit);

        assertEquals(DATA_URI, this.converter.convert(ATTACHMENT_URL));
        verify(this.mockitoOldcore.getMockContextualAuthorizationManager()).checkAccess(Right.VIEW, DOCUMENT_REFERENCE);
    }

    @Test
    void conversionWithRevision() throws DiffException, IOException, XWikiException, AuthorizationException
    {
        String url = ATTACHMENT_URL + "?rev=1.1";

        XWikiDocument document = setUpDocument(url);

        String revision = "1.1";
        when(this.documentRevisionProvider.getRevision(DOCUMENT_REFERENCE, revision))
            .thenReturn(document);

        assertEquals(DATA_URI, this.converter.convert(url));
        verify(this.documentRevisionProvider).getRevision(DOCUMENT_REFERENCE, revision);
        verify(this.documentRevisionProvider).checkAccess(Right.VIEW, CurrentUserReference.INSTANCE, DOCUMENT_REFERENCE,
            revision);
    }

    @Test
    void conversionWithResizing() throws DiffException, IOException, XWikiException
    {
        String url = ATTACHMENT_URL + "?width=200&height=100";

        setUpDocument(url);

        // Mock the image plugin
        XWikiPluginInterface imagePlugin = mock();
        String pluginName = "image";
        when(imagePlugin.getName()).thenReturn(pluginName);
        XWikiPluginManager mockPluginManager = mock();
        when(mockPluginManager.getPlugin(pluginName)).thenReturn(imagePlugin);
        when(this.mockitoOldcore.getSpyXWiki().getPluginManager()).thenReturn(mockPluginManager);

        XWikiAttachment resizedImage = mock();
        when(resizedImage.getMimeType(any())).thenReturn("image/png");
        when(resizedImage.getContentInputStream(any()))
            .thenReturn(new ByteArrayInputStream("resizedContent".getBytes()));

        when(imagePlugin.downloadAttachment(any(XWikiAttachment.class), any())).then(invocation -> {
            XWikiAttachment attachment = invocation.getArgument(0);
            XWikiContext context = invocation.getArgument(1);

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
        when(this.configuration.getMaximumDataURISize()).thenReturn(1024L * 1024L);

        DiffException exception = assertThrows(DiffException.class, () -> this.converter.convert(ATTACHMENT_URL));
        assertEquals(String.format("The attachment [%s] is too big.", ATTACHMENT_REFERENCE), exception.getMessage());
    }

    private XWikiDocument setUpDocument(String url) throws IOException, XWikiException
    {
        when(this.resolver.resolve(url, EntityType.ATTACHMENT)).thenReturn(ATTACHMENT_REFERENCE);

        XWikiDocument document = new XWikiDocument(DOCUMENT_REFERENCE);
        document.setAttachment(ATTACHMENT_NAME, new ByteArrayInputStream("attachmentContent".getBytes()),
            this.mockitoOldcore.getXWikiContext());
        this.mockitoOldcore.getSpyXWiki().saveDocument(document, this.mockitoOldcore.getXWikiContext());

        return document;
    }
}
