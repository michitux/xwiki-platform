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

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the default methods in recipient index contracts.
 *
 * @version $Id$
 * @since 18.4.0
 */
class NotificationRecipientContractsTest
{
    @Test
    void notificationRecipientIndexDefaultRemoveIsNoOp()
    {
        NotificationRecipientIndex notificationRecipientIndex = new NotificationRecipientIndex()
        {
            @Override
            public void addOrUpdate(IndexableNotificationFilterPreference preference)
            {
            }

            @Override
            public void remove(IndexableNotificationFilterPreference preference)
            {
            }

            @Override
            public NotificationCandidateSet findCandidates(NotificationEventDescriptor eventDescriptor)
            {
                return new NotificationCandidateSet();
            }
        };

        assertDoesNotThrow(() -> notificationRecipientIndex.remove("NFP_42"));
    }

    @Test
    void notificationRecipientIndexManagerDefaultGetIfPresentReturnsEmptyOptional()
    {
        NotificationRecipientIndexManager notificationRecipientIndexManager = new NotificationRecipientIndexManager()
        {
            @Override
            public NotificationRecipientIndex getOrBuildIndex(String wikiId)
            {
                return null;
            }

            @Override
            public void invalidateWiki(String wikiId)
            {
            }

            @Override
            public void clear()
            {
            }
        };

        Optional<NotificationRecipientIndex> result = notificationRecipientIndexManager.getIfPresent("xwiki");

        assertTrue(result.isEmpty());
        assertEquals(Optional.empty(), result);
    }
}
