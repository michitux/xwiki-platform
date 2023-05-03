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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.diff.DiffException;
import org.xwiki.diff.xml.XMLDiffConfiguration;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.security.authorization.AuthorizationException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.user.CurrentUserReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.DocumentRevisionProvider;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.XWikiPluginInterface;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiServletRequestStub;

/**
 * Attachment loading implementation of {@link DataURIConverter}.
 *
 * @version $Id$
 * @since 14.10.10
 * @since 15.4RC1
 */
@Component
@Singleton
@Named("attachment")
public class AttachmentDataURIConverter implements DataURIConverter
{
    private static final String REV_PARAMETER = "rev";

    private static final String QUERY_SEPARATOR = "?";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("resource/standardURL")
    private EntityReferenceResolver<String> resolver;

    @Inject
    private ContextualAuthorizationManager authorization;

    @Inject
    private DocumentRevisionProvider revisionProvider;

    @Inject
    private XMLDiffConfiguration xmlDiffConfiguration;

    @Override
    public String convert(String url) throws DiffException
    {
        if (url.startsWith("data:")) {
            // Already data URI.
            return url;
        }

        // Get the attachment reference corresponding to the URL.
        EntityReference entityReference = this.resolver.resolve(url, EntityType.ATTACHMENT);
        if (!(entityReference instanceof AttachmentReference)) {
            throw new DiffException("Failed to resolve the URL [" + url + "] to an attachment reference.");
        }

        AttachmentReference attachmentReference = (AttachmentReference) entityReference;

        Map<String, String[]> parameterMap = getParameterMap(url);
        XWikiContext xcontext = this.xcontextProvider.get();

        try {
            XWikiDocument document = getDocument(attachmentReference.getDocumentReference(), parameterMap, xcontext);
            if (document == null) {
                throw new DiffException(String.format("Failed to find the document [%s].",
                    attachmentReference.getDocumentReference()));
            }

            XWikiAttachment attachment = document.getAttachment(attachmentReference.getName());

            if (attachment == null) {
                throw new DiffException(String.format("Failed to find the attachment [%s].", attachmentReference));
            }

            attachment = resizeImage(attachment, parameterMap, xcontext);

            long maximumAttachmentSize = this.xmlDiffConfiguration.getMaximumDataURISize();
            if (maximumAttachmentSize > 0 && attachment.getLongSize() > maximumAttachmentSize) {
                throw new DiffException(String.format("The attachment [%s] is too big.", attachmentReference));
            }

            return getDataURI(attachment, xcontext);
        } catch (XWikiException e) {
            throw new DiffException(
                String.format("Failed to get the document [%s].", attachmentReference.getDocumentReference()), e);
        } catch (IOException e) {
            throw new DiffException(
                String.format("Failed to get the attachment content [%s].", attachmentReference), e);
        } catch (AuthorizationException e) {
            throw new DiffException(
                String.format("The user doesn't have the right to access the document [%s].",
                    attachmentReference.getDocumentReference()), e);
        }
    }

    /**
     * Get the document for the given document reference and parameters, checking view rights for the current user.
     *
     * @param documentReference the document reference
     * @param parameterMap the parameters from which the revision shall be extracted
     * @param xcontext the XWiki context
     * @return the document
     * @throws AuthorizationException if the user doesn't have the right to access the document
     * @throws XWikiException if an error happens when getting the document
     */
    private XWikiDocument getDocument(DocumentReference documentReference, Map<String, String[]> parameterMap,
        XWikiContext xcontext) throws AuthorizationException, XWikiException
    {
        XWikiDocument document;

        // Check if the user has view right on the document.
        this.authorization.checkAccess(Right.VIEW, documentReference);

        // Get the document revision if specified.
        if (parameterMap.containsKey(REV_PARAMETER)) {
            String rev = parameterMap.get(REV_PARAMETER)[0];
            // Check if the user has view right on the document revision.
            this.revisionProvider.checkAccess(Right.VIEW, CurrentUserReference.INSTANCE, documentReference, rev);
            document = this.revisionProvider.getRevision(documentReference, rev);
        } else {
            document = xcontext.getWiki().getDocument(documentReference, xcontext);
        }

        return document;
    }

    /**
     * Call the image plugin to get the resized image if necessary.
     *
     * @param attachment the attachment to resize
     * @param parameterMap the parameters for resizing
     * @param xcontext the XWiki context
     * @return the resized attachment or the original attachment if no resizing is necessary
     */
    private XWikiAttachment resizeImage(XWikiAttachment attachment, Map<String, String[]> parameterMap,
        XWikiContext xcontext)
    {
        XWikiAttachment result;

        if (Stream.of("width", "height", "quality").anyMatch(parameterMap::containsKey)) {

            // Backup the request to be able to restore it later.
            XWikiRequest backupRequest = xcontext.getRequest();

            try {
                // The image plugin reads the request parameters to get the image size, so fake a request with the
                // parameters.
                XWikiRequest stubRequest =
                    new XWikiServletRequestStub.Builder().setRequestParameters(parameterMap).build();
                xcontext.setRequest(stubRequest);

                XWikiPluginInterface imagePlugin = xcontext.getWiki().getPluginManager().getPlugin("image");
                result = imagePlugin.downloadAttachment(attachment, xcontext);
            } finally {
                // Restore the original request.
                xcontext.setRequest(backupRequest);
            }
        } else {
            result = attachment;
        }
        return result;
    }

    private String getDataURI(XWikiAttachment attachment, XWikiContext xcontext) throws IOException, XWikiException
    {
        String contentType = attachment.getMimeType(xcontext);
        byte[] content = IOUtils.toByteArray(attachment.getContentInputStream(xcontext));
        return String.format("data:%s;base64,%s", contentType, Base64.getEncoder().encodeToString(content));
    }

    private Map<String, String[]> getParameterMap(String url)
    {
        Map<String, String[]> parameterMap;
        if (url.contains(QUERY_SEPARATOR)) {
            String query = StringUtils.substringAfter(url, QUERY_SEPARATOR);
            parameterMap = URLEncodedUtils.parse(query, StandardCharsets.UTF_8).stream()
                .collect(Collectors.toMap(NameValuePair::getName, pair -> new String[] { pair.getValue() }));
        } else {
            parameterMap = Map.of();
        }
        return parameterMap;
    }
}
