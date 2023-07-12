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
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.diff.DiffException;
import org.xwiki.diff.xml.XMLDiffDataURIConverterConfiguration;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.AttachmentReferenceResolver;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.resource.CreateResourceReferenceException;
import org.xwiki.security.authorization.AuthorizationException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.url.ExtendedURL;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.XWikiPluginManager;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiServletRequestStub;

/**
 * Attachment loading implementation of {@link DataURIConverter}.
 *
 * @version $Id$
 * @since 14.10.13
 * @since 15.5RC1
 */
@Component
@Singleton
@Named("attachment")
public class AttachmentDataURIConverter extends AbstractDataURIConverter
{
    private static final String REV_PARAMETER = "rev";

    private static final String RECYCLE_BIN_ID_PARAMETER = "rid";

    private static final String ID = "id";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("downloadURL")
    private AttachmentReferenceResolver<ExtendedURL> attachmentReferenceResolver;

    @Inject
    private ContextualAuthorizationManager authorization;

    @Inject
    private XMLDiffDataURIConverterConfiguration xmlDiffDataURIConverterConfiguration;

    @Override
    public String convert(String url) throws DiffException
    {
        if (url.startsWith("data:")) {
            // Already data URI.
            return url;
        }

        XWikiContext context = this.xcontextProvider.get();
        URL absoluteURL = getAbsoluteURL(url, context);
        ExtendedURL extendedURL;
        try {
            extendedURL = new ExtendedURL(absoluteURL, context.getRequest().getContextPath());
        } catch (CreateResourceReferenceException e) {
            throw new DiffException(String.format("Failed to create an extended URL from the URL [%s].", url), e);
        }

        AttachmentReference attachmentReference = this.attachmentReferenceResolver.resolve(extendedURL);

        if (attachmentReference == null) {
            throw new DiffException(String.format("Failed to resolve an attachment reference from the URL [%s].", url));
        }

        Map<String, List<String>> parameterMap = extendedURL.getParameters();
        try {
            XWikiAttachment attachment = getAttachment(context, attachmentReference, parameterMap);

            attachment = resizeImage(attachment, parameterMap, context);

            long maximumAttachmentSize = this.xmlDiffDataURIConverterConfiguration.getMaximumContentSize();
            if (maximumAttachmentSize > 0 && attachment.getLongSize() > maximumAttachmentSize) {
                throw new DiffException(String.format("The attachment [%s] is too big.", attachmentReference));
            }

            return getDataURI(attachment, context);
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

    private XWikiAttachment getAttachment(XWikiContext context, AttachmentReference attachmentReference,
        Map<String, List<String>> parameterMap) throws AuthorizationException, XWikiException, DiffException
    {
        DocumentReference documentReference = attachmentReference.getDocumentReference();

        // Check if the user has view right on the document.
        this.authorization.checkAccess(Right.VIEW, documentReference);

        XWikiDocument document = context.getWiki().getDocument(documentReference, context);

        XWikiAttachment attachment;
        String filename = attachmentReference.getName();

        // The following code closely follows the code of DownloadRevAction.
        if (parameterMap.containsKey(RECYCLE_BIN_ID_PARAMETER) && context.getWiki().hasAttachmentRecycleBin(context)) {
            // Retrieve the attachment from the recycle bin.
            int recycleId = Integer.parseInt(parameterMap.get(RECYCLE_BIN_ID_PARAMETER).get(0));
            attachment = new XWikiAttachment(document, filename);
            attachment = context.getWiki().getAttachmentRecycleBinStore()
                .restoreFromRecycleBin(attachment, recycleId, context, true);
        } else if (parameterMap.containsKey(ID)) {
            // Retrieve the attachment from the list of attachments by position as this is also supported by
            // DownloadRevAction.
            int id = Integer.parseInt(parameterMap.get(ID).get(0));
            attachment = document.getAttachmentList().get(id);
        } else {
            attachment = document.getAttachment(filename);
        }

        if (attachment == null) {
            throw new DiffException(String.format("Failed to find the attachment [%s].", attachmentReference));
        }

        if (parameterMap.containsKey(REV_PARAMETER)) {
            synchronized (attachment) {
                XWikiAttachment oldAttachment = attachment.getAttachmentRevision(parameterMap.get(REV_PARAMETER).get(0),
                    context);
                if (oldAttachment != null) {
                    attachment = oldAttachment;
                }
            }
        }

        return attachment;
    }

    /**
     * Call the image plugin to get the resized image if necessary.
     *
     * @param attachment the attachment to resize
     * @param parameterMap the parameters for resizing
     * @param xcontext the XWiki context
     * @return the resized attachment or the original attachment if no resizing is necessary
     */
    private XWikiAttachment resizeImage(XWikiAttachment attachment, Map<String, List<String>> parameterMap,
        XWikiContext xcontext)
    {
        // Backup the request to be able to restore it later.
        XWikiRequest backupRequest = xcontext.getRequest();

        try {
            // The image plugin reads the request parameters to get the image size, so fake a request with the
            // parameters.
            Map<String, String[]> requestParameters = parameterMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toArray(new String[0])));
            XWikiRequest stubRequest =
                new XWikiServletRequestStub.Builder().setRequestParameters(requestParameters).build();
            xcontext.setRequest(stubRequest);

            XWikiPluginManager plugins = xcontext.getWiki().getPluginManager();
            return plugins.downloadAttachment(attachment, xcontext);
        } finally {
            // Restore the original request.
            xcontext.setRequest(backupRequest);
        }
    }

    private String getDataURI(XWikiAttachment attachment, XWikiContext xcontext) throws IOException, XWikiException
    {
        String contentType = attachment.getMimeType(xcontext);
        byte[] content = IOUtils.toByteArray(attachment.getContentInputStream(xcontext));
        return getDataURI(contentType, content);
    }
}
