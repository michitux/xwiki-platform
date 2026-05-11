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
import org.xwiki.eventstream.Event;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultNotificationRecipientResolver}.
 *
 * @version $Id$
 * @since 18.4.0
 */
@ComponentTest
class DefaultNotificationRecipientResolverTest
{
    @InjectMockComponents
    private DefaultNotificationRecipientResolver notificationRecipientResolver;

    @MockComponent
    private NotificationRecipientIndexManager notificationRecipientIndexManager;

    @MockComponent
    private WikiDescriptorManager wikiDescriptorManager;

    @Test
    void resolveCandidateUsersDelegatesToIndexManager() throws Exception
    {
        Event event = mock(Event.class);
        NotificationRecipientIndex notificationRecipientIndex = mock(NotificationRecipientIndex.class);
        DocumentReference user = new DocumentReference("xwiki", "XWiki", "User");

        when(event.getWiki()).thenReturn(new WikiReference("xwiki"));
        when(this.wikiDescriptorManager.getMainWikiId()).thenReturn("xwiki");
        when(this.notificationRecipientIndexManager.getOrBuildIndex("xwiki")).thenReturn(notificationRecipientIndex);
        when(notificationRecipientIndex.findCandidates(event)).thenReturn(Set.of(user));

        assertEquals(Set.of(user), this.notificationRecipientResolver.resolveCandidateUsers(event));
    }

    @Test
    void resolveCandidateUsersIncludesMainWikiIndexForSubwikiEvents() throws Exception
    {
        Event event = mock(Event.class);
        NotificationRecipientIndex subwikiNotificationRecipientIndex = mock(NotificationRecipientIndex.class, "subwiki");
        NotificationRecipientIndex mainWikiNotificationRecipientIndex = mock(NotificationRecipientIndex.class, "main");
        DocumentReference subUser = new DocumentReference("subwiki", "XWiki", "SubUser");
        DocumentReference mainUser = new DocumentReference("xwiki", "XWiki", "MainUser");

        when(event.getWiki()).thenReturn(new WikiReference("subwiki"));
        when(this.wikiDescriptorManager.getMainWikiId()).thenReturn("xwiki");
        when(this.notificationRecipientIndexManager.getOrBuildIndex("subwiki")).thenReturn(subwikiNotificationRecipientIndex);
        when(this.notificationRecipientIndexManager.getOrBuildIndex("xwiki")).thenReturn(mainWikiNotificationRecipientIndex);
        when(subwikiNotificationRecipientIndex.findCandidates(event)).thenReturn(Set.of(subUser));
        when(mainWikiNotificationRecipientIndex.findCandidates(event)).thenReturn(Set.of(mainUser));

        assertEquals(Set.of(subUser, mainUser), this.notificationRecipientResolver.resolveCandidateUsers(event));
    }
}
