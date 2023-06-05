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

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.diff.DiffException;
import org.xwiki.diff.xml.XMLDiffDataURIConverterConfiguration;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultDataURIConverter}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultDataURIConverterTest
{
    private static final String IMAGE_URL = "/image.png";

    @MockComponent
    @Named("attachment")
    private DataURIConverter attachmentDataURIConverter;

    @MockComponent
    @Named("http")
    private DataURIConverter httpDataURIConverter;

    @MockComponent
    private XMLDiffDataURIConverterConfiguration configuration;

    @MockComponent
    @Named("context")
    private ComponentManager componentManager;

    @InjectMockComponents
    private DefaultDataURIConverter defaultDataURIConverter;

    @BeforeEach
    public void setUp(MockitoComponentManager mockitoComponentManager) throws ComponentLookupException
    {
        when(this.componentManager.getInstance(eq(DataURIConverter.class), anyString()))
            .then(invocation ->
                mockitoComponentManager.getInstance(DataURIConverter.class, invocation.getArgument(1)));
    }

    @Test
    void convertWithUnknownHint()
    {
        when(this.configuration.getConverterHint()).thenReturn("unknown");

        DiffException exception = assertThrows(DiffException.class, () -> {
            this.defaultDataURIConverter.convert(IMAGE_URL);
        });

        assertEquals("Failed to find a data URI converter for hint [unknown].", exception.getMessage());
    }

    @Test
    void convertWithAttachmentHint() throws DiffException
    {
        when(this.configuration.getConverterHint()).thenReturn("attachment");

        this.defaultDataURIConverter.convert(IMAGE_URL);

        verify(this.attachmentDataURIConverter).convert(IMAGE_URL);
    }

    @Test
    void convertWithHttpHint() throws DiffException
    {
        when(this.configuration.getConverterHint()).thenReturn("http");

        this.defaultDataURIConverter.convert(IMAGE_URL);

        verify(this.httpDataURIConverter).convert(IMAGE_URL);
    }
}
