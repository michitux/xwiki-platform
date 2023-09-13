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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.platform.security.requiredrights.RequiredRightAnalysisResult;
import org.xwiki.platform.security.requiredrights.RequiredRightAnalyzer;
import org.xwiki.platform.security.requiredrights.RequiredRightsException;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;

/**
 * Analyzer that checks if an XDOM contains macros that require more rights than the current author has.
 *
 * @version $Id$
 * @since 15.8RC1
 */
@Component
@Singleton
@Named(XDOMRequiredRightAnalyzer.ID)
public class XDOMRequiredRightAnalyzer implements RequiredRightAnalyzer<XDOM>
{
    /**
     * The id of this analyzer.
     */
    public static final String ID = "xdom";

    @Inject
    private Provider<ComponentManager> componentManagerProvider;

    /** Provider for the default macro block analyzer to avoid a cyclic dependency. */
    @Inject
    @Named(DefaultMacroBlockRequiredRightAnalyzer.ID)
    private Provider<RequiredRightAnalyzer<MacroBlock>> defaultMacroBlockRequiredRightAnalyzer;

    @Override
    public List<RequiredRightAnalysisResult> analyze(XDOM dom) throws RequiredRightsException
    {
        // Find all macros in the XDOM.
        List<MacroBlock> macroBlocks = dom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);

        List<RequiredRightAnalysisResult> result = new ArrayList<>();
        RequiredRightAnalyzer<MacroBlock> analyzer = this.defaultMacroBlockRequiredRightAnalyzer.get();

        // For each macro, find a corresponding analyzer. If there is none, use the default macro block analyzer.
        for (MacroBlock macroBlock : macroBlocks) {
            result.addAll(analyzer.analyze(macroBlock));
        }

        return result;
    }
}
