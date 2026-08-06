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
package org.xwiki.rendering.internal.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.script.ScriptContext;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.logging.Message;
import org.xwiki.logging.marker.TranslationMarker;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.CompositeBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.internal.transformation.MutableRenderingContext;
import org.xwiki.rendering.limits.RecursionLimitExceededException;
import org.xwiki.rendering.limits.RenderingLimits;
import org.xwiki.rendering.limits.RenderingLimitsScope;
import org.xwiki.rendering.macro.MacroRenderingLimitExceededException;
import org.xwiki.rendering.transformation.RenderingContext;
import org.xwiki.script.ScriptContextManager;
import org.xwiki.template.Template;
import org.xwiki.template.TemplateManager;

/**
 * Extends the default {@link DefaultErrorBlockGenerator} to add support for translations and templates.
 * 
 * @version $Id$
 * @since 14.0RC1
 */
@Component
@Singleton
public class XWikiErrorBlockGenerator extends DefaultErrorBlockGenerator
{
    private static final String CONTEXT_ATTRIBUTE = "renderingerror";

    private static final String ECONTEXT_MARKER = "rendering.error.xwiki.template";

    @Inject
    private ComponentManager componentManager;

    @Inject
    private RenderingContext renderingContext;

    @Inject
    private RenderingLimits renderingLimits;

    @Inject
    private Provider<ScriptContextManager> scriptContextManagerProvider;

    @Inject
    private Execution execution;

    private List<Block> executeTemplate(String messageId, Message message, Message description, boolean inline)
    {
        ExecutionContext econtext = this.execution.getContext();

        // Protect against infinite loop
        // Support use case where no TemplateManager implementation is available
        if (econtext.hasProperty(ECONTEXT_MARKER) || !this.componentManager.hasComponent(TemplateManager.class)) {
            return null;
        }

        TemplateManager templateManager;
        try {
            templateManager = this.componentManager.getInstance(TemplateManager.class);
        } catch (ComponentLookupException e) {
            this.logger.error("Failed to lookup TemplateManger component", e);

            return null;
        }

        // Try to find a template associated to the specific message id
        Template template = messageId != null
            ? templateManager.getTemplate(CLASS_ATTRIBUTE_MESSAGE_VALUE + '/' + messageId + ".vm") : null;

        // Fallback on the default template
        if (template == null) {
            template = templateManager.getTemplate(CLASS_ATTRIBUTE_MESSAGE_VALUE + "/default.vm");
        }

        if (template != null) {
            return executeTemplate(template, templateManager, messageId, message, description, inline, econtext);
        }

        return null;
    }

    private List<Block> executeTemplate(Template template, TemplateManager templateManager, String messageId,
        Message message, Message description, boolean inline, ExecutionContext econtext)
    {
        ScriptContext scriptContext = this.scriptContextManagerProvider.get().getCurrentScriptContext();

        // Remember the current value in the context
        Object currentRenderingerror = scriptContext.getAttribute(CONTEXT_ATTRIBUTE, ScriptContext.GLOBAL_SCOPE);

        boolean renderingContextPushed = false;
        try {
            // Indicate that we are executing a rendering error template
            econtext.newProperty(ECONTEXT_MARKER).initial(true).declare();

            // Expose error information to the scripts through a $renderingerror variable
            scriptContext.setAttribute(CONTEXT_ATTRIBUTE,
                createRenderingError(messageId, message, description, inline), ScriptContext.GLOBAL_SCOPE);

            // Disable restricted context if set as the error generator template generally needs scripting
            if (this.renderingContext.isRestricted()
                && this.renderingContext instanceof MutableRenderingContext mutableRenderingContext) {
                // Make the current velocity template id available
                mutableRenderingContext.push(this.renderingContext.getTransformation(),
                    this.renderingContext.getXDOM(), this.renderingContext.getDefaultSyntax(),
                    this.renderingContext.getTransformationId(), false, this.renderingContext.getTargetSyntax());

                renderingContextPushed = true;
            }

            // Execute the template. Allow it to use the reserve of the rendering limits as executing the template
            // triggers a transformation, which would otherwise immediately hit the very limit we might be reporting.
            Block block;
            try (RenderingLimitsScope reserve = this.renderingLimits.enterReserve()) {
                block = templateManager.execute(template, inline);
            }

            List<Block> blocks = block instanceof XDOM || block instanceof CompositeBlock ? block.getChildren()
                : Collections.singletonList(block);

            return containsMacro(blocks) ? null : blocks;
        } catch (Exception e) {
            if (isCausedByRenderingLimit(e)) {
                // Rendering the message hit a limit itself, which happens when the reserve that is meant for the error
                // messages of a rendering is used up, e.g. because a lot of them had to be generated. The exhausted
                // limit has been reported already and the plain message of the parent class is used instead, so there
                // is nothing to report here.
                this.logger.debug("Failed to generate the error rendering message as a rendering limit has been"
                    + " reached", e);
            } else {
                this.logger.error("Failed to generate error rendering message", e);
            }
        } finally {
            // Get rid of temporary rendering context
            if (renderingContextPushed) {
                ((MutableRenderingContext) this.renderingContext).pop();
            }

            // Restore the previous context value
            scriptContext.setAttribute(CONTEXT_ATTRIBUTE, currentRenderingerror, ScriptContext.GLOBAL_SCOPE);

            // We don't execute a renderingerror template anymore
            econtext.removeProperty(ECONTEXT_MARKER);
        }

        return null;
    }

    /**
     * @param messageId the id of the message, if any
     * @param message the message of the error
     * @param description the description of the error
     * @param inline whether the error is generated in an inline context
     * @return the information about the error to expose to the template as {@code $renderingerror}
     */
    private Map<String, Object> createRenderingError(String messageId, Message message, Message description,
        boolean inline)
    {
        Map<String, Object> renderingerror = new HashMap<>();
        renderingerror.put("messageId", messageId);
        renderingerror.put("message", message);
        renderingerror.put("description", description);
        renderingerror.put("inline", inline);

        if (message.getThrowable() != null) {
            Throwable rootCause = ExceptionUtils.getRootCause(message.getThrowable());
            renderingerror.put("rootCause", rootCause != null ? rootCause : message.getThrowable());
            renderingerror.put("stackTrace", ExceptionUtils.getStackTrace(message.getThrowable()));
        }

        return renderingerror;
    }

    /**
     * @param throwable the failure of the template execution
     * @return {@code true} when the template failed because a rendering limit has been reached
     */
    private boolean isCausedByRenderingLimit(Throwable throwable)
    {
        return ExceptionUtils.throwableOfType(throwable, MacroRenderingLimitExceededException.class) != null
            || ExceptionUtils.throwableOfType(throwable, RecursionLimitExceededException.class) != null;
    }

    /**
     * Check whether the result of the template still contains a macro that hasn't been executed, which happens when a
     * rendering limit stopped the transformation of the template.
     * <p>
     * Such a macro would be executed by the transformation this error message is inserted into, outside of the reserve
     * scope, so it would be stopped by the very limit that is being reported and get an error message of its own in the
     * place of this one — an error about the {@code [box]} macro of the template instead of the failure that happened.
     * The plain message of the parent class is used instead, as it contains no macro at all.
     *
     * @param blocks the blocks the template produced
     * @return {@code true} when any of the blocks is or contains a macro that hasn't been executed
     */
    private boolean containsMacro(List<Block> blocks)
    {
        ClassBlockMatcher macroMatcher = new ClassBlockMatcher(MacroBlock.class);

        return blocks.stream()
            .anyMatch(block -> block.getFirstBlock(macroMatcher, Block.Axes.DESCENDANT_OR_SELF) != null);
    }

    @Override
    protected List<Block> generateErrorBlocks(boolean inline, Message message, Message description)
    {
        String messageId;
        if (message.getMarker() instanceof TranslationMarker translationMarker) {
            messageId = translationMarker.getTranslationKey();
        } else {
            messageId = null;
        }

        List<Block> blocks = executeTemplate(messageId, message, description, inline);

        if (blocks != null) {
            return blocks;
        }

        // Fallback on default behavior
        return super.generateErrorBlocks(inline, message, description);
    }
}
