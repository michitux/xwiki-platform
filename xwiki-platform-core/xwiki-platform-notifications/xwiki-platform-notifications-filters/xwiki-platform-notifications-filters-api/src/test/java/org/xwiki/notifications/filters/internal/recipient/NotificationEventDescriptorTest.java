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
package org.xwiki.notifications.filters.internal.recipient;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.notifications.NotificationFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link NotificationEventDescriptor}.
 *
 * @version $Id$
 * @since 18.4.0
 */
class NotificationEventDescriptorTest
{
    @Test
    void builderCreatesImmutableDescriptor()
    {
        Date eventDate = new Date();
        List<String> spaces = new ArrayList<>(List.of("xwiki:Space", "xwiki:Parent"));

        NotificationEventDescriptor descriptor = NotificationEventDescriptor.builder()
            .wikiId("xwiki")
            .documentReference("xwiki:Space.Page")
            .spaceReferences(spaces)
            .eventType("update")
            .actor("xwiki:XWiki.Actor")
            .eventDate(eventDate)
            .format(NotificationFormat.ALERT)
            .build();

        spaces.add("xwiki:Injected");
        eventDate.setTime(0);

        assertEquals("xwiki", descriptor.getWikiId());
        assertEquals("xwiki:Space.Page", descriptor.getDocumentReference());
        assertEquals(List.of("xwiki:Space", "xwiki:Parent"), descriptor.getSpaceReferences());
        assertEquals("update", descriptor.getEventType());
        assertEquals("xwiki:XWiki.Actor", descriptor.getActor());
        assertEquals(NotificationFormat.ALERT, descriptor.getFormat());
        assertNotSame(eventDate, descriptor.getEventDate());
        assertThrows(UnsupportedOperationException.class,
            () -> descriptor.getSpaceReferences().add("xwiki:Other"));
    }
}
