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

    @Test
    void resolveCandidateUsersDelegatesToIndexManager() throws Exception
    {
        NotificationRecipientIndex notificationRecipientIndex = mock(NotificationRecipientIndex.class);
        NotificationCandidateSet notificationCandidateSet = new NotificationCandidateSet();
        notificationCandidateSet.addScopeCandidateUser("xwiki:XWiki.User");
        notificationCandidateSet.addFollowedUserCandidateUser("xwiki:XWiki.OtherUser");
        NotificationEventDescriptor eventDescriptor = NotificationEventDescriptor.builder().wikiId("xwiki").build();

        when(this.notificationRecipientIndexManager.getOrBuildIndex("xwiki")).thenReturn(notificationRecipientIndex);
        when(notificationRecipientIndex.findCandidates(eventDescriptor)).thenReturn(notificationCandidateSet);

        assertEquals(Set.of("xwiki:XWiki.User", "xwiki:XWiki.OtherUser"),
            this.notificationRecipientResolver.resolveCandidateUsers(eventDescriptor));
    }
}
