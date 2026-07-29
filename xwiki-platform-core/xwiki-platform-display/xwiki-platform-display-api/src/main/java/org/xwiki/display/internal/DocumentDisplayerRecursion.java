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
package org.xwiki.display.internal;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.limits.RecursionType;
import org.xwiki.rendering.limits.RenderingLimits;
import org.xwiki.rendering.limits.RenderingLimitsScope;

/**
 * Guards the document displayers against displaying a document within itself, which can happen for instance when a
 * script in the title or in the content displays the current document again.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Component(roles = DocumentDisplayerRecursion.class)
@Singleton
public class DocumentDisplayerRecursion
{
    /**
     * Displaying the title of a document while already displaying the title of the same document is always an error, so
     * the limit is one.
     */
    private static final RecursionType TITLE = new RecursionType("display.title", 1, 1);

    /**
     * The number of recursive displays of the content of a single document that are allowed until we stop. We need this
     * to be at least two when a document with a sheet displays the content, as it is the case in App Within Minutes.
     * Set it to five to be sure that it is enough.
     */
    private static final RecursionType CONTENT = new RecursionType("display.content", 5, 1);

    @Inject
    private RenderingLimits renderingLimits;

    @Inject
    private EntityReferenceSerializer<String> defaultEntityReferenceSerializer;

    /**
     * @param reference the reference of the document whose title is about to be displayed
     * @return the entered recursion level to be closed once the title has been displayed, or an empty optional when the
     *         title of that document is already being displayed
     */
    public Optional<RenderingLimitsScope> enterTitle(DocumentReference reference)
    {
        return enter(TITLE, reference);
    }

    /**
     * @param reference the reference of the document whose content is about to be displayed
     * @return the entered recursion level to be closed once the content has been displayed, or an empty optional when
     *         the limit of recursive displays of that document's content has been reached
     */
    public Optional<RenderingLimitsScope> enterContent(DocumentReference reference)
    {
        return enter(CONTENT, reference);
    }

    private Optional<RenderingLimitsScope> enter(RecursionType type, DocumentReference reference)
    {
        return this.renderingLimits.tryEnter(type, this.defaultEntityReferenceSerializer.serialize(reference));
    }
}
