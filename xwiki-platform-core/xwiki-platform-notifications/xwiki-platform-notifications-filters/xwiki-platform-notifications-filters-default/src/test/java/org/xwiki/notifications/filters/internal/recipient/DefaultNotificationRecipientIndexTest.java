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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.eventstream.Event;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.notifications.NotificationFormat;
import org.xwiki.notifications.filters.NotificationFilterType;
import org.xwiki.notifications.filters.internal.DefaultNotificationFilterPreference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultNotificationRecipientIndex}.
 *
 * @version $Id$
 * @since 18.4.0
 */
class DefaultNotificationRecipientIndexTest
{
    private static final String PAGE_REFERENCE = "xwiki:Space.Page";

    private static final String SPACE_REFERENCE = "xwiki:Space";

    private static final String ACTOR_REFERENCE = "xwiki:XWiki.Actor";

    private EntityReferenceSerializer<String> entityReferenceSerializer;

    private DocumentReferenceResolver<String> documentReferenceResolver;

    private DefaultNotificationRecipientIndex index;

    @BeforeEach
    void setUp()
    {
        this.entityReferenceSerializer = mock(EntityReferenceSerializer.class);
        this.documentReferenceResolver = mock(DocumentReferenceResolver.class);
        this.index = new DefaultNotificationRecipientIndex(this.entityReferenceSerializer, this.documentReferenceResolver);
    }

    @Test
    void findCandidatesUsesEventTypeSpecificAndAllTypesBuckets()
    {
        DocumentReference pageWatcher = new DocumentReference("xwiki", "XWiki", "PageWatcher");
        DocumentReference wikiWatcher = new DocumentReference("xwiki", "XWiki", "WikiWatcher");
        DocumentReference actorWatcher = new DocumentReference("xwiki", "XWiki", "ActorWatcher");
        DocumentReference pageReference = new DocumentReference("xwiki", "Space", "Page");
        DocumentReference actorReference = new DocumentReference("xwiki", "XWiki", "Actor");
        Event updateEvent = mock(Event.class, "updateEvent");
        Event deleteEvent = mock(Event.class, "deleteEvent");

        when(this.documentReferenceResolver.resolve("xwiki:XWiki.PageWatcher")).thenReturn(pageWatcher);
        when(this.documentReferenceResolver.resolve("xwiki:XWiki.WikiWatcher")).thenReturn(wikiWatcher);
        when(this.documentReferenceResolver.resolve("xwiki:XWiki.ActorWatcher")).thenReturn(actorWatcher);
        when(this.entityReferenceSerializer.serialize(pageReference)).thenReturn(PAGE_REFERENCE);
        when(this.entityReferenceSerializer.serialize(pageReference.getLastSpaceReference())).thenReturn(SPACE_REFERENCE);
        when(this.entityReferenceSerializer.serialize(actorReference)).thenReturn(ACTOR_REFERENCE);

        this.index.addOrUpdate(createScopePreference("NFP_1", "xwiki:XWiki.PageWatcher", PAGE_REFERENCE, null, null,
            Set.of()));
        this.index.addOrUpdate(createScopePreference("NFP_2", "xwiki:XWiki.WikiWatcher", null, null, "xwiki",
            Set.of("update")));
        this.index.addOrUpdate(createFollowedUserPreference("NFP_3", "xwiki:XWiki.ActorWatcher", ACTOR_REFERENCE));

        when(updateEvent.getWiki()).thenReturn(new WikiReference("xwiki"));
        when(updateEvent.getDocument()).thenReturn(pageReference);
        when(updateEvent.getType()).thenReturn("update");
        when(updateEvent.getUser()).thenReturn(actorReference);

        assertEquals(Set.of(pageWatcher, wikiWatcher, actorWatcher), this.index.findCandidates(updateEvent));

        when(deleteEvent.getWiki()).thenReturn(new WikiReference("xwiki"));
        when(deleteEvent.getDocument()).thenReturn(pageReference);
        when(deleteEvent.getType()).thenReturn("delete");

        assertEquals(Set.of(pageWatcher), this.index.findCandidates(deleteEvent));
    }

    @Test
    void addOrUpdateReplacesPreviousEntry()
    {
        DocumentReference owner = new DocumentReference("xwiki", "XWiki", "User");
        DocumentReference oldPage = new DocumentReference("xwiki", "Old", "Page");
        DocumentReference newPage = new DocumentReference("xwiki", "New", "Page");
        Event oldEvent = mock(Event.class, "oldEvent");
        Event newEvent = mock(Event.class, "newEvent");

        when(this.documentReferenceResolver.resolve("xwiki:XWiki.User")).thenReturn(owner);
        when(this.entityReferenceSerializer.serialize(oldPage)).thenReturn("xwiki:Old.Page");
        when(this.entityReferenceSerializer.serialize(oldPage.getLastSpaceReference())).thenReturn("xwiki:Old");
        when(this.entityReferenceSerializer.serialize(newPage)).thenReturn("xwiki:New.Page");
        when(this.entityReferenceSerializer.serialize(newPage.getLastSpaceReference())).thenReturn("xwiki:New");

        this.index.addOrUpdate(createScopePreference("NFP_4", "xwiki:XWiki.User", "xwiki:Old.Page", null, null,
            Set.of()));
        this.index.addOrUpdate(createScopePreference("NFP_4", "xwiki:XWiki.User", "xwiki:New.Page", null, null,
            Set.of()));

        when(oldEvent.getWiki()).thenReturn(new WikiReference("xwiki"));
        when(oldEvent.getDocument()).thenReturn(oldPage);
        when(newEvent.getWiki()).thenReturn(new WikiReference("xwiki"));
        when(newEvent.getDocument()).thenReturn(newPage);

        assertTrue(this.index.findCandidates(oldEvent).isEmpty());
        assertEquals(Set.of(owner), this.index.findCandidates(newEvent));

        this.index.remove("NFP_4");
        assertTrue(this.index.findCandidates(newEvent).isEmpty());
    }

    private DefaultNotificationFilterPreference createScopePreference(String preferenceId, String owner, String pageOnly,
        String page, String wiki, Set<String> eventTypes)
    {
        DefaultNotificationFilterPreference preference = new DefaultNotificationFilterPreference();
        preference.setId(preferenceId);
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

    private DefaultNotificationFilterPreference createFollowedUserPreference(String preferenceId, String owner,
        String user)
    {
        DefaultNotificationFilterPreference preference = new DefaultNotificationFilterPreference();
        preference.setId(preferenceId);
        preference.setOwner(owner);
        preference.setEnabled(true);
        preference.setFilterName("eventUserNotificationFilter");
        preference.setFilterType(NotificationFilterType.INCLUSIVE);
        preference.setNotificationFormats(Set.of(NotificationFormat.ALERT));
        preference.setUser(user);
        return preference;
    }
}
