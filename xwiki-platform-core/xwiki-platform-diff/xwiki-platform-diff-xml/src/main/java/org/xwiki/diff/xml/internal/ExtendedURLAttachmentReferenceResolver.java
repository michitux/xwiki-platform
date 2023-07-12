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

import java.net.URL;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.AttachmentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.resource.CreateResourceReferenceException;
import org.xwiki.resource.CreateResourceTypeException;
import org.xwiki.resource.ResourceReferenceResolver;
import org.xwiki.resource.ResourceType;
import org.xwiki.resource.ResourceTypeResolver;
import org.xwiki.resource.UnsupportedResourceReferenceException;
import org.xwiki.resource.entity.EntityResourceAction;
import org.xwiki.resource.entity.EntityResourceReference;
import org.xwiki.url.ExtendedURL;
import org.xwiki.url.internal.URLValidator;

/**
 * Resolves an {@link AttachmentReference} from an {@link URL}.
 *
 * @version $Id$
 * @since 14.10.14
 * @since 15.6RC1
 * @since 15.5.1
 */
@Component
@Singleton
@Named("downloadURL")
public class ExtendedURLAttachmentReferenceResolver implements AttachmentReferenceResolver<ExtendedURL>
{
    private static final EntityResourceAction DOWNLOAD_ACTION = new EntityResourceAction("download");

    private static final EntityResourceAction DOWNLOAD_REV_ACTION = new EntityResourceAction("downloadrev");

    private static final List<EntityResourceAction> SUPPORTED_ACTIONS = List.of(DOWNLOAD_ACTION, DOWNLOAD_REV_ACTION);

    @Inject
    private ResourceReferenceResolver<ExtendedURL> resolver;

    @Inject
    private ResourceTypeResolver<ExtendedURL> typeResolver;

    @Inject
    @Named("standardLocal")
    private URLValidator localURLValidator;

    @Inject
    private Logger logger;

    @Override
    public AttachmentReference resolve(ExtendedURL url, Object... parameters)
    {
        if (!this.localURLValidator.validate(url)) {
            this.logger.warn("URL [{}] is not a local URL", url.getWrappedURL());
            return null;
        }

        try {
            ResourceType type = this.typeResolver.resolve(url, Collections.emptyMap());

            if (type.getId().equals("entity")) {
                EntityResourceReference err =
                    (EntityResourceReference) this.resolver.resolve(url, type, Collections.emptyMap());

                EntityReference entityReference = err.getEntityReference();
                if (SUPPORTED_ACTIONS.contains(err.getAction())
                    && entityReference.getType().equals(EntityType.ATTACHMENT))
                {
                    return new AttachmentReference(entityReference);
                }
            }
        } catch (IllegalArgumentException | UnsupportedResourceReferenceException
                 | CreateResourceReferenceException | CreateResourceTypeException e) {
            this.logger.warn("Failed to resolve attachment reference from URL [{}]", url, e);
        }

        return null;
    }
}
