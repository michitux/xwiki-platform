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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.xwiki.diff.xml.XMLDiffDataURIConverterConfiguration;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.security.authorization.AuthorizationException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

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
 * @since 14.10.11
 * @since 15.4RC1
 */
@Component
@Singleton
@Named("attachment")
public class AttachmentDataURIConverter implements DataURIConverter
{
    private static final String REV_PARAMETER = "rev";

    private static final String QUERY_SEPARATOR = "?";

    private static final String RID = "rid";

    private static final String ID = "id";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("resource/standardURL")
    private EntityReferenceResolver<String> resolver;

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
        String absoluteURL = getAbsoluteURL(url, context);

        // Get the attachment reference corresponding to the URL.
        EntityReference entityReference = this.resolver.resolve(absoluteURL, EntityType.ATTACHMENT);
        AttachmentReference attachmentReference;
        try {
            attachmentReference = new AttachmentReference(entityReference);
        } catch (IllegalArgumentException e) {
            throw new DiffException("Failed to resolve the URL [" + absoluteURL + "] to an attachment reference.");
        }

        Map<String, String[]> parameterMap = getParameterMap(url);
        try {
            XWikiAttachment attachment = getAttachment(context, attachmentReference, parameterMap);

            attachment = resizeImage(attachment, parameterMap, context);

            long maximumAttachmentSize = this.xmlDiffDataURIConverterConfiguration.getMaximumDataURISize();
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
        Map<String, String[]> parameterMap) throws AuthorizationException, XWikiException, DiffException
    {
        DocumentReference documentReference = attachmentReference.getDocumentReference();

        // Check if the user has view right on the document.
        this.authorization.checkAccess(Right.VIEW, documentReference);

        XWikiDocument document = context.getWiki().getDocument(documentReference, context);

        XWikiAttachment attachment;
        String filename = attachmentReference.getName();

        if (parameterMap.containsKey(RID) && context.getWiki().hasAttachmentRecycleBin(context)) {
            int recycleId = Integer.parseInt(parameterMap.get(RID)[0]);
            attachment = new XWikiAttachment(document, filename);
            attachment = context.getWiki().getAttachmentRecycleBinStore()
                .restoreFromRecycleBin(attachment, recycleId, context, true);
        } else if (parameterMap.containsKey(ID)) {
            int id = Integer.parseInt(parameterMap.get(ID)[0]);
            attachment = document.getAttachmentList().get(id);
        } else {
            attachment = document.getAttachment(filename);
        }

        if (attachment == null) {
            throw new DiffException(String.format("Failed to find the attachment [%s].", attachmentReference));
        }

        if (parameterMap.containsKey(REV_PARAMETER)) {
            synchronized (attachment) {
                XWikiAttachment oldAttachment = attachment.getAttachmentRevision(parameterMap.get(REV_PARAMETER)[0],
                    context);
                if (oldAttachment != null) {
                    attachment = oldAttachment;
                }
            }
        }

        return attachment;
    }

    private String getAbsoluteURL(String url, XWikiContext xcontext) throws DiffException
    {
        String absoluteURL;
        try {
            if (xcontext.getRequest() != null && xcontext.getRequest().getHttpServletRequest() != null) {
                URL requestURL = new URL(xcontext.getRequest().getHttpServletRequest().getRequestURL().toString());
                absoluteURL = new URL(requestURL, url).toString();
            } else {
                absoluteURL = new URL(url).toString();
            }
        } catch (MalformedURLException e) {
            throw new DiffException(String.format("Failed to resolve [%s] to an absolute URL.", url), e);
        }
        return absoluteURL;
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
        // Backup the request to be able to restore it later.
        XWikiRequest backupRequest = xcontext.getRequest();

        try {
            // The image plugin reads the request parameters to get the image size, so fake a request with the
            // parameters.
            XWikiRequest stubRequest =
                new XWikiServletRequestStub.Builder().setRequestParameters(parameterMap).build();
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
