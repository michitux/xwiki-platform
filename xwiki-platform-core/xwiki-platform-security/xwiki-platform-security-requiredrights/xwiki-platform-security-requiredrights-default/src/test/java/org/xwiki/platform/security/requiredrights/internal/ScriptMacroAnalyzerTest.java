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
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.component.internal.ContextComponentManagerProvider;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.ObjectPropertyReference;
import org.xwiki.platform.security.requiredrights.RequiredRightAnalysisResult;
import org.xwiki.properties.BeanManager;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.macro.script.MacroPermissionPolicy;
import org.xwiki.rendering.macro.script.PrivilegedScriptMacro;
import org.xwiki.rendering.macro.script.ScriptMacroParameters;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xpn.xwiki.objects.BaseObjectReference;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xpn.xwiki.web.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for {@link ScriptMacroAnalyzer}.
 *
 * @version $Id$
 */
@ComponentTest
@ReferenceComponentList
@ComponentList({ ContextComponentManagerProvider.class })
class ScriptMacroAnalyzerTest
{
    @InjectMockComponents
    private ScriptMacroAnalyzer analyzer;

    @MockComponent
    private BeanManager beanManager;

    @MockComponent
    private TranslationMessageSupplierProvider translationMessageSupplierProvider;

    @MockComponent
    @Named("myScript")
    private MacroPermissionPolicy macroPermissionPolicy;

    @Test
    void analyzeWithMacroPermissionPolicy(MockitoComponentManager componentManager) throws Exception
    {
        // Make sure that the BaseObjectReference constructor works as it needs Utils to have the component manager.
        Utils.setComponentManager(componentManager);

        // Create the macro block.
        String macroId = "myScript";
        String macroContent = "#Script";
        Map<String, String> parameters = Map.of("key", "value");
        MacroBlock macroBlock = new MacroBlock(macroId, parameters, macroContent, false);

        // Create XDOM to get the source reference from.
        MetaData metaData = new MetaData();
        new XDOM(List.of(macroBlock), metaData);
        DocumentReference documentReference = new DocumentReference("xwiki", "Space", "Page");
        DocumentReference classReference = new DocumentReference("xwiki", "XWiki", "Class");
        BaseObjectReference objectReference = new BaseObjectReference(classReference, 1, documentReference);
        ObjectPropertyReference propertyReference = new ObjectPropertyReference("testProperty", objectReference);
        metaData.addMetaData(XDOMRequiredRightAnalyzer.ENTITY_REFERENCE_METADATA, propertyReference);

        // Mock the descriptor with the parameters bean class.
        Macro<?> macro = mock();
        MacroDescriptor macroDescriptor = mock(MacroDescriptor.class);
        when(macro.getDescriptor()).thenReturn(macroDescriptor);
        doReturn(ScriptMacroParameters.class).when(macroDescriptor).getParametersBeanClass();

        Right myRight = mock();
        when(this.macroPermissionPolicy.getRequiredRight()).thenReturn(myRight);

        MacroTransformationContext macroTransformationContext = mock();
        List<RequiredRightAnalysisResult> analysisResults =
            this.analyzer.analyze(macroBlock, macro);

        verify(this.beanManager).populate(any(ScriptMacroParameters.class), eq(parameters));
        verify(this.translationMessageSupplierProvider).get("security.requiredrights.scriptmacro", macroId, myRight);

        assertEquals(1, analysisResults.size());
        RequiredRightAnalysisResult analysisResult = analysisResults.get(0);
        assertEquals(propertyReference, analysisResult.getEntityReference());
        RequiredRightAnalysisResult.RequiredRight requiredRight = analysisResult.getRequiredRights().get(0);
        assertEquals(myRight, requiredRight.getRight());
        assertFalse(requiredRight.isOptional());
        assertEquals(EntityType.DOCUMENT, requiredRight.getEntityType());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void analyzeWithFallback(boolean isPrivileged) throws ComponentLookupException
    {
        String macroId = "fallback";

        String macroContent = "#Script";
        Map<String, String> parameters = Map.of("key", "value");
        MacroBlock macroBlock = new MacroBlock(macroId, parameters, macroContent, false);

        // Create XDOM with source metadata set to check that it is resolved.
        MetaData metaData = new MetaData();
        new XDOM(List.of(macroBlock), metaData);
        metaData.addMetaData(MetaData.SOURCE, "xwiki:Space.Page");

        MacroDescriptor macroDescriptor = mock(MacroDescriptor.class);
        doReturn(ScriptMacroParameters.class).when(macroDescriptor).getParametersBeanClass();

        Macro<?> macro = isPrivileged ? mock(withSettings().extraInterfaces(PrivilegedScriptMacro.class)) : mock();
        when(macro.getDescriptor()).thenReturn(macroDescriptor);

        Right requiredRight = isPrivileged ? Right.PROGRAM : Right.SCRIPT;

        List<RequiredRightAnalysisResult> analysisResults = this.analyzer.analyze(macroBlock, macro);

        // Check that the translation message supplier was called.
        verify(this.translationMessageSupplierProvider)
            .get("security.requiredrights.scriptmacro", macroId, requiredRight);
        // Check that the result is correct.
        assertEquals(1, analysisResults.size());
        RequiredRightAnalysisResult analysisResult = analysisResults.get(0);
        assertEquals(new DocumentReference("xwiki", "Space", "Page"), analysisResult.getEntityReference());
        RequiredRightAnalysisResult.RequiredRight requiredRightResult = analysisResult.getRequiredRights().get(0);
        assertEquals(requiredRight, requiredRightResult.getRight());
        assertFalse(requiredRightResult.isOptional());
        assertEquals(EntityType.DOCUMENT, requiredRightResult.getEntityType());
    }
}
