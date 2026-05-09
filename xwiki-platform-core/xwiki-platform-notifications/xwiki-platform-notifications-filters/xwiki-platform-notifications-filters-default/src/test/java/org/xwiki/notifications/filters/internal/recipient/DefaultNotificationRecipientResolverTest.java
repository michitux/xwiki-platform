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
        NotificationRecipientIndex notificationRecipientIndex = mock(NotificationRecipientIndex.class);
        NotificationCandidateSet notificationCandidateSet = new NotificationCandidateSet();
        notificationCandidateSet.addScopeCandidateUser("xwiki:XWiki.User");
        notificationCandidateSet.addFollowedUserCandidateUser("xwiki:XWiki.OtherUser");
        NotificationEventDescriptor eventDescriptor = NotificationEventDescriptor.builder().wikiId("xwiki").build();

        when(this.wikiDescriptorManager.getMainWikiId()).thenReturn("xwiki");
        when(this.notificationRecipientIndexManager.getOrBuildIndex("xwiki")).thenReturn(notificationRecipientIndex);
        when(notificationRecipientIndex.findCandidates(eventDescriptor)).thenReturn(notificationCandidateSet);

        assertEquals(Set.of("xwiki:XWiki.User", "xwiki:XWiki.OtherUser"),
            this.notificationRecipientResolver.resolveCandidateUsers(eventDescriptor));
    }

    @Test
    void resolveCandidateUsersIncludesMainWikiIndexForSubwikiEvents() throws Exception
    {
        NotificationRecipientIndex subwikiNotificationRecipientIndex = mock(NotificationRecipientIndex.class, "subwiki");
        NotificationRecipientIndex mainWikiNotificationRecipientIndex = mock(NotificationRecipientIndex.class, "main");
        NotificationCandidateSet subwikiCandidates = new NotificationCandidateSet();
        NotificationCandidateSet mainWikiCandidates = new NotificationCandidateSet();
        NotificationEventDescriptor eventDescriptor = NotificationEventDescriptor.builder().wikiId("subwiki").build();

        subwikiCandidates.addScopeCandidateUser("subwiki:XWiki.SubUser");
        mainWikiCandidates.addFollowedUserCandidateUser("xwiki:XWiki.MainUser");

        when(this.wikiDescriptorManager.getMainWikiId()).thenReturn("xwiki");
        when(this.notificationRecipientIndexManager.getOrBuildIndex("subwiki")).thenReturn(subwikiNotificationRecipientIndex);
        when(this.notificationRecipientIndexManager.getOrBuildIndex("xwiki")).thenReturn(mainWikiNotificationRecipientIndex);
        when(subwikiNotificationRecipientIndex.findCandidates(eventDescriptor)).thenReturn(subwikiCandidates);
        when(mainWikiNotificationRecipientIndex.findCandidates(eventDescriptor)).thenReturn(mainWikiCandidates);

        assertEquals(Set.of("subwiki:XWiki.SubUser", "xwiki:XWiki.MainUser"),
            this.notificationRecipientResolver.resolveCandidateUsers(eventDescriptor));
    }
}
