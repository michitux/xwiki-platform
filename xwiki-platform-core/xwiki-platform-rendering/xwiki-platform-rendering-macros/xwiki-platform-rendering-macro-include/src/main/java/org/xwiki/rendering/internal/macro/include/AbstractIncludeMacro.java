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
package org.xwiki.rendering.internal.macro.include;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.display.internal.DocumentDisplayer;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.SectionBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.limits.RecursionLimitExceededException;
import org.xwiki.rendering.limits.RecursionType;
import org.xwiki.rendering.limits.RenderingLimits;
import org.xwiki.rendering.limits.RenderingLimitsScope;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.util.ParserUtils;
import org.xwiki.security.authorization.ContextualAuthorizationManager;

/**
 * Common code for both Include and Display macros.
 *
 * @param <P> the type of the macro parameter class
 * @version $Id$
 * @since 12.4RC1
 */
public abstract class AbstractIncludeMacro<P> extends AbstractMacro<P>
{
    /**
     * Including or displaying a document within itself is always an error, so the limit is one.
     * <p>
     * The include and the display macro deliberately share this single recursion type so that a cycle that alternates
     * between them, like {@code A --include--> B --display--> A}, is caught, too.
     */
    private static final RecursionType INCLUSION = new RecursionType("macro.inclusion", 1, 1);

    private static final String RECURSION_MESSAGE = "Found recursive %s of document [%s]";

    /**
     * Used to access document content and check view access right.
     */
    @Inject
    protected DocumentAccessBridge documentAccessBridge;

    @Inject
    protected ContextualAuthorizationManager contextualAuthorization;

    /**
     * Used to serialize resolved document links into a string again since the Rendering API only manipulates Strings
     * (done voluntarily to be independent of any wiki engine and not draw XWiki-specific dependencies).
     */
    @Inject
    protected EntityReferenceSerializer<String> defaultEntityReferenceSerializer;

    /**
     * Used to display the content of the included document.
     */
    @Inject
    @Named("configured")
    protected DocumentDisplayer documentDisplayer;

    /**
     * Used to transform the passed reference macro parameter into a complete {@link DocumentReference} one.
     */
    @Inject
    @Named("macro")
    protected EntityReferenceResolver<String> macroEntityReferenceResolver;

    /**
     * Used to catch recursive inclusions/displays.
     */
    @Inject
    protected RenderingLimits renderingLimits;

    private final ParserUtils parserUtils = new ParserUtils();

    protected AbstractIncludeMacro(String name, String description, Class<?> parametersBeanClass)
    {
        super(name, description, parametersBeanClass);
    }

    /**
     * Allows overriding the Document Displayer used (useful for unit tests).
     *
     * @param documentDisplayer the new Document Displayer to use
     */
    public void setDocumentDisplayer(DocumentDisplayer documentDisplayer)
    {
        this.documentDisplayer = documentDisplayer;
    }

    protected void excludeFirstHeading(XDOM xdom)
    {
        // We handle 2 cases:
        // - case 1: Including a document. In this case we're expecting the first block to be a SectionBlock.
        // - case 2: Including a document section. In this case we're expecting the first block to be a HeaderBlock
        //   since the include is returning what's inside the SectionBlock.
        xdom.getChildren().stream().findFirst().filter(
                block -> block instanceof SectionBlock || block instanceof HeaderBlock)
            .ifPresent(sectionOrHeaderBlock -> {
                if (sectionOrHeaderBlock instanceof SectionBlock) {
                    List<Block> sectionChildren = sectionOrHeaderBlock.getChildren();
                    sectionChildren.stream().findFirst().filter(HeaderBlock.class::isInstance)
                        .ifPresent(headerBlock ->
                            xdom.replaceChild(sectionChildren.subList(1, sectionChildren.size()),
                                sectionOrHeaderBlock));
                } else {
                    xdom.removeBlock(sectionOrHeaderBlock);
                }
            });
    }

    protected EntityReference resolve(MacroBlock block, String reference, EntityType type, String messageText)
        throws MacroExecutionException
    {
        if (reference == null) {
            throw new MacroExecutionException(String.format("You must specify a 'reference' parameter pointing to the "
                + "entity to %s.", messageText));
        }

        return this.macroEntityReferenceResolver.resolve(reference, type, block);
    }

    /**
     * Enter a recursion level for the given document, protecting from recursive include/display.
     * <p>
     * Note that the level only covers displaying the document, it is closed again as soon as the display returned. With
     * {@code context="current"} the content is instead inserted into the content being transformed and thus executed by
     * the outer transformation, i.e., outside this level. That case is covered by
     * {@link IncludeMacro#checkBlockRecursion(Block, EntityReference)}, which walks the block tree of the content being
     * transformed, and, when the inclusion is itself nested in another include or display, additionally by the still
     * open level of that enclosing macro.
     *
     * @param reference the reference of the document being included/displayed
     * @param messageText the portion of text to insert in the error message when an error occurs, to represent the
     *                    action done (e.g. "inclusion", "display")
     * @return the entered level, to be closed once the document has been displayed
     * @throws MacroExecutionException recursive inclusion/display has been found
     */
    protected RenderingLimitsScope enterRecursion(DocumentReference reference, String messageText)
        throws MacroExecutionException
    {
        try {
            return this.renderingLimits.enter(INCLUSION, this.defaultEntityReferenceSerializer.serialize(reference));
        } catch (RecursionLimitExceededException e) {
            throw new MacroExecutionException(String.format(RECURSION_MESSAGE, messageText, reference), e);
        }
    }

    protected void maybeConvertToInline(XDOM result, MacroTransformationContext context)
    {
        // Only remove the top-level paragraph for backwards-compatibility. If the first block is a macro,
        // ParserUtils#convertToInline converts that macro to inline, but that causes regressions when the include macro
        // is used inside the HTML macro in an inline parsing context that isn't in an inline HTML context and includes
        // a single macro as in Main.AllDocs.
        // Only modify things when there is only a single child to avoid copying many children in cases there is
        // nothing to do.
        if (context.isInline() && result.getChildren().size() == 1) {
            List<Block> modifiableChildren = new ArrayList<>(result.getChildren());
            this.parserUtils.removeTopLevelParagraph(modifiableChildren);
            result.setChildren(modifiableChildren);
        }
    }
}
