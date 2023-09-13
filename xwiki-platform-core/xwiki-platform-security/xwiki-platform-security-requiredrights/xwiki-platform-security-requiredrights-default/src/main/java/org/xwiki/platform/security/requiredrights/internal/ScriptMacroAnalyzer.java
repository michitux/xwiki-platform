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
package org.xwiki.platform.security.requiredrights.internal;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.EntityType;
import org.xwiki.platform.security.requiredrights.RequiredRightAnalysisResult;
import org.xwiki.properties.BeanManager;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.script.MacroPermissionPolicy;
import org.xwiki.rendering.macro.script.PrivilegedScriptMacro;
import org.xwiki.rendering.macro.script.ScriptMacroParameters;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

/**
 * Component for analyzing a script macro.
 *
 * @version $Id$
 */
@Component(roles = ScriptMacroAnalyzer.class)
@Singleton
public class ScriptMacroAnalyzer
{
    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    @Inject
    private BeanManager beanManager;

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    /**
     * @param macroBlock the macro block to analyze
     * @param macro the macro of the block
     * @param transformationContext the transformation context for the macro
     * @return the required rights for the macro
     */
    public List<RequiredRightAnalysisResult> analyze(MacroBlock macroBlock, Macro<?> macro,
        MacroTransformationContext transformationContext)
    {
        List<RequiredRightAnalysisResult> result;

        try {
            Object macroParameters =
                macro.getDescriptor().getParametersBeanClass().getDeclaredConstructor().newInstance();
            this.beanManager.populate(macroParameters, macroBlock.getParameters());

            MacroPermissionPolicy mpp =
                this.componentManagerProvider.get()
                    .getInstance(MacroPermissionPolicy.class, macroBlock.getId());
            if (mpp.hasPermission((ScriptMacroParameters) macroParameters, transformationContext)) {
                // TODO: generate a warning if the user has script rights but not programming rights?
                result = List.of();
            } else {
                result = generateScriptMacroError(macroBlock, macroBlock.getId(), mpp.getRequiredRight());
            }
        } catch (Exception ex) {
            if (macro instanceof PrivilegedScriptMacro) {
                if (!this.authorizationManager.hasAccess(Right.PROGRAM)) {
                    result = generateScriptMacroError(macroBlock, macroBlock.getId(), Right.PROGRAM);
                } else {
                    result = List.of();
                }
            } else if (!this.authorizationManager.hasAccess(Right.SCRIPT)) {
                result = generateScriptMacroError(macroBlock, macroBlock.getId(), Right.SCRIPT);
            } else {
                // TODO: generate a warning as the user has script rights but not programming rights?
                result = List.of();
            }
        }
        return result;
    }

    private static List<RequiredRightAnalysisResult> generateScriptMacroError(MacroBlock macroBlock, String macroId,
        Right right)
    {
        return List.of(new RequiredRightAnalysisResult(DefaultMacroBlockRequiredRightAnalyzer.ID,
            "security.requiredrights.scriptmacro", List.of(macroId, macroBlock.getContent()),
            right, EntityType.DOCUMENT));
    }
}
