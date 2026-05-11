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

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.notifications.filters.internal.event.NotificationFilterPreferenceAddOrUpdatedEvent;
import org.xwiki.notifications.filters.internal.event.NotificationFilterPreferenceDeletedEvent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link NotificationRecipientIndexListener}.
 *
 * @version $Id$
 */
@ComponentTest
class NotificationRecipientIndexListenerTest
{
    @InjectMockComponents
    private NotificationRecipientIndexListener listener;

    @MockComponent(classToMock = DefaultNotificationRecipientIndexManager.class)
    private NotificationRecipientIndexManager notificationRecipientIndexManager;

    @Test
    void addOrUpdateUsesThePreferenceSourceAndOwnerData() throws Exception
    {
        IndexableNotificationFilterPreference preference = mock(IndexableNotificationFilterPreference.class);
        DocumentReference owner = new DocumentReference("xwiki", "XWiki", "User");

        this.listener.onEvent(new NotificationFilterPreferenceAddOrUpdatedEvent(), preference, owner);

        verify((DefaultNotificationRecipientIndexManager) this.notificationRecipientIndexManager)
            .addOrUpdatePreference("xwiki", preference);
    }

    @Test
    void deleteUsesTheDeletedPreferenceIdsFromEventData() throws Exception
    {
        WikiReference wikiReference = new WikiReference("xwiki");
        Set<String> preferenceIds = Set.of("NFP_42");

        this.listener.onEvent(new NotificationFilterPreferenceDeletedEvent(), wikiReference, preferenceIds);

        verify((DefaultNotificationRecipientIndexManager) this.notificationRecipientIndexManager)
            .removePreferences("xwiki", preferenceIds);
    }
}
