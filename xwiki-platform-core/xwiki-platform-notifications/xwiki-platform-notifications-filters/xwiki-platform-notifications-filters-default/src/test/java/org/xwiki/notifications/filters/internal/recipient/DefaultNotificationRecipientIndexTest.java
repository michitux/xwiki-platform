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

import java.util.Date;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xwiki.notifications.NotificationFormat;
import org.xwiki.notifications.filters.NotificationFilterType;
import org.xwiki.notifications.filters.internal.DefaultNotificationFilterPreference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DefaultNotificationRecipientIndex}.
 *
 * @version $Id$
 * @since 18.4.0
 */
class DefaultNotificationRecipientIndexTest
{
    @Test
    void findCandidatesUsesEventTypeSpecificAndAllTypesBuckets()
    {
        DefaultNotificationRecipientIndex index = new DefaultNotificationRecipientIndex();
        index.addOrUpdate(createScopePreference(1L, "xwiki:XWiki.PageWatcher", "xwiki:Space.Page", null, null,
            Set.of()));
        index.addOrUpdate(createScopePreference(2L, "xwiki:XWiki.WikiWatcher", null, null, "xwiki",
            Set.of("update")));
        index.addOrUpdate(createFollowedUserPreference(3L, "xwiki:XWiki.ActorWatcher", "xwiki:XWiki.Actor"));

        NotificationCandidateSet updateCandidates = index.findCandidates(NotificationEventDescriptor.builder()
            .wikiId("xwiki")
            .documentReference("xwiki:Space.Page")
            .spaceReferences(List.of("xwiki:Space"))
            .eventType("update")
            .actor("xwiki:XWiki.Actor")
            .eventDate(new Date())
            .format(NotificationFormat.ALERT)
            .build());

        assertEquals(Set.of("xwiki:XWiki.PageWatcher", "xwiki:XWiki.WikiWatcher"),
            updateCandidates.getScopeCandidateUsers());
        assertEquals(Set.of("xwiki:XWiki.ActorWatcher"), updateCandidates.getFollowedUserCandidateUsers());
        assertTrue(updateCandidates.hasBroadWikiMatch());

        NotificationCandidateSet deleteCandidates = index.findCandidates(NotificationEventDescriptor.builder()
            .wikiId("xwiki")
            .documentReference("xwiki:Space.Page")
            .spaceReferences(List.of("xwiki:Space"))
            .eventType("delete")
            .build());

        assertEquals(Set.of("xwiki:XWiki.PageWatcher"), deleteCandidates.getScopeCandidateUsers());
        assertFalse(deleteCandidates.hasBroadWikiMatch());
    }

    @Test
    void addOrUpdateReplacesPreviousEntry()
    {
        DefaultNotificationRecipientIndex index = new DefaultNotificationRecipientIndex();
        index.addOrUpdate(createScopePreference(4L, "xwiki:XWiki.User", "xwiki:Old.Page", null, null, Set.of()));
        index.addOrUpdate(createScopePreference(4L, "xwiki:XWiki.User", "xwiki:New.Page", null, null, Set.of()));

        NotificationCandidateSet oldCandidates = index.findCandidates(NotificationEventDescriptor.builder()
            .wikiId("xwiki")
            .documentReference("xwiki:Old.Page")
            .build());
        NotificationCandidateSet newCandidates = index.findCandidates(NotificationEventDescriptor.builder()
            .wikiId("xwiki")
            .documentReference("xwiki:New.Page")
            .build());

        assertTrue(oldCandidates.getCandidateUsers().isEmpty());
        assertEquals(Set.of("xwiki:XWiki.User"), newCandidates.getCandidateUsers());

        index.remove("NFP_4");
        assertTrue(index.findCandidates(NotificationEventDescriptor.builder().wikiId("xwiki")
            .documentReference("xwiki:New.Page").build()).getCandidateUsers().isEmpty());
    }

    private DefaultNotificationFilterPreference createScopePreference(long internalId, String owner, String pageOnly,
        String page, String wiki, Set<String> eventTypes)
    {
        DefaultNotificationFilterPreference preference = new DefaultNotificationFilterPreference();
        preference.setInternalId(internalId);
        preference.setOwner(owner);
        preference.setEnabled(true);
        preference.setFilterName("scopeNotificationFilter");
        preference.setFilterType(NotificationFilterType.INCLUSIVE);
        preference.setNotificationFormats(Set.of(NotificationFormat.ALERT));
        preference.setEventTypes(eventTypes);
        preference.setPageOnly(pageOnly);
        preference.setPage(page);
        preference.setWiki(wiki);
        return preference;
    }

    private DefaultNotificationFilterPreference createFollowedUserPreference(long internalId, String owner, String user)
    {
        DefaultNotificationFilterPreference preference = new DefaultNotificationFilterPreference();
        preference.setInternalId(internalId);
        preference.setOwner(owner);
        preference.setEnabled(true);
        preference.setFilterName("eventUserNotificationFilter");
        preference.setFilterType(NotificationFilterType.INCLUSIVE);
        preference.setNotificationFormats(Set.of(NotificationFormat.ALERT));
        preference.setUser(user);
        return preference;
    }
}
