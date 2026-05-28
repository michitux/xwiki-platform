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
package org.xwiki.rendering.macro.jexl;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.observation.internal.DefaultObservationManager;
import org.xwiki.properties.BeanDescriptor;
import org.xwiki.properties.BeanManager;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.internal.macro.jexl.JexlMacro;
import org.xwiki.rendering.internal.macro.jexl.JexlMacroPermissionPolicy;
import org.xwiki.rendering.internal.macro.script.PermissionCheckerListener;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verify that a JEXL macro's execution can be restricted.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({JexlMacroPermissionPolicy.class, DefaultObservationManager.class, PermissionCheckerListener.class})
class JexlMacroSecurityTest
{
    @MockComponent
    private ContextualAuthorizationManager authorizationManager;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @MockComponent
    private MacroManager macroManager;

    @MockComponent
    private BeanManager beanManager;

    @InjectMockComponents
    private JexlMacro jexlMacro;

    @BeforeEach
    void setUp() throws Exception
    {
        BeanDescriptor mockBeanDescriptor = mock(BeanDescriptor.class);
        when(mockBeanDescriptor.getProperties()).thenReturn(Collections.emptyList());

        when(this.beanManager.getBeanDescriptor(any(Class.class))).thenReturn(mockBeanDescriptor);
        when(this.macroManager.getMacro(any(MacroId.class))).thenReturn((Macro) this.jexlMacro);
    }

    @Test
    void restrictedByContext()
    {
        MacroTransformationContext context = new MacroTransformationContext();
        context.setSyntax(Syntax.XWIKI_2_0);
        context.setCurrentMacroBlock(new MacroBlock("jexl", Collections.emptyMap(), false));
        context.setId("page1");
        context.getTransformationContext().setRestricted(true);

        when(this.authorizationManager.hasAccess(Right.SCRIPT)).thenReturn(true);

        assertThrows(MacroExecutionException.class, () -> this.jexlMacro.execute(new org.xwiki.rendering.macro.script.ScriptMacroParameters(), "42", context));
    }

    @Test
    void restrictedByRights()
    {
        MacroTransformationContext context = new MacroTransformationContext();
        context.setSyntax(Syntax.XWIKI_2_0);
        context.setCurrentMacroBlock(new MacroBlock("jexl", Collections.emptyMap(), false));
        context.setId("page1");
        context.getTransformationContext().setRestricted(false);

        when(this.authorizationManager.hasAccess(Right.SCRIPT)).thenReturn(false);

        assertThrows(MacroExecutionException.class, () -> this.jexlMacro.execute(new org.xwiki.rendering.macro.script.ScriptMacroParameters(), "42", context));

        verify(this.authorizationManager).hasAccess(Right.SCRIPT);
    }
}
