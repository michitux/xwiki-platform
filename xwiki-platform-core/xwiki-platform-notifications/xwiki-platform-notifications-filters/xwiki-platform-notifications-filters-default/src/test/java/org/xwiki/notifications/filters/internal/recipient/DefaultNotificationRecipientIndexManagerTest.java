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

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xwiki.eventstream.Event;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.notifications.filters.NotificationFilterType;
import org.xwiki.notifications.filters.internal.DefaultNotificationFilterPreference;
import org.xwiki.notifications.filters.internal.NotificationFilterPreferenceStore;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultNotificationRecipientIndexManager}.
 *
 * @version $Id$
 * @since 18.4.0
 */
@ComponentTest
class DefaultNotificationRecipientIndexManagerTest
{
    @InjectMockComponents
    private DefaultNotificationRecipientIndexManager notificationRecipientIndexManager;

    @MockComponent
    private NotificationFilterPreferenceStore notificationFilterPreferenceStore;

    @MockComponent
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    @MockComponent
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Test
    void getOrBuildIndexBuildsOnce() throws Exception
    {
        DefaultNotificationFilterPreference preference = createPreference(12L, "xwiki:XWiki.User", "xwiki:Space.Page");
        DocumentReference owner = new DocumentReference("xwiki", "XWiki", "User");

        when(this.notificationFilterPreferenceStore.loadIndexablePreferencesBatch("xwiki", 0, 100))
            .thenReturn(List.of(preference));
        when(this.documentReferenceResolver.resolve("xwiki:XWiki.User")).thenReturn(owner);
        NotificationRecipientIndex firstIndex = this.notificationRecipientIndexManager.getOrBuildIndex("xwiki");
        NotificationRecipientIndex secondIndex = this.notificationRecipientIndexManager.getOrBuildIndex("xwiki");

        assertSame(firstIndex, secondIndex);
        assertTrue(this.notificationRecipientIndexManager.getIfPresent("xwiki").isPresent());
        verify(this.notificationFilterPreferenceStore, times(1))
            .loadIndexablePreferencesBatch("xwiki", 0, 100);
    }

    @Test
    void refreshUserReplacesOnlyTheAffectedOwnerPreferences() throws Exception
    {
        DocumentReference owner = new DocumentReference("xwiki", "XWiki", "User");
        DocumentReference oldPage = new DocumentReference("xwiki", "Old", "Page");
        DocumentReference newPage = new DocumentReference("xwiki", "New", "Page");
        Event oldEvent = mock(Event.class, "oldEvent");
        Event newEvent = mock(Event.class, "newEvent");
        DefaultNotificationFilterPreference oldPreference =
            createPreference(12L, "xwiki:XWiki.User", "xwiki:Old.Page");
        DefaultNotificationFilterPreference newPreference =
            createPreference(13L, "xwiki:XWiki.User", "xwiki:New.Page");

        when(this.notificationFilterPreferenceStore.loadIndexablePreferencesBatch("xwiki", 0, 100))
            .thenReturn(List.of(oldPreference));
        when(this.notificationFilterPreferenceStore.getPreferencesOfUser(owner)).thenReturn(List.of(newPreference));
        when(this.documentReferenceResolver.resolve("xwiki:XWiki.User")).thenReturn(owner);
        when(this.entityReferenceSerializer.serialize(owner)).thenReturn("xwiki:XWiki.User");
        when(this.entityReferenceSerializer.serialize(oldPage)).thenReturn("xwiki:Old.Page");
        when(this.entityReferenceSerializer.serialize(oldPage.getLastSpaceReference())).thenReturn("xwiki:Old");
        when(this.entityReferenceSerializer.serialize(newPage)).thenReturn("xwiki:New.Page");
        when(this.entityReferenceSerializer.serialize(newPage.getLastSpaceReference())).thenReturn("xwiki:New");
        when(oldEvent.getWiki()).thenReturn(new WikiReference("xwiki"));
        when(oldEvent.getDocument()).thenReturn(oldPage);
        when(newEvent.getWiki()).thenReturn(new WikiReference("xwiki"));
        when(newEvent.getDocument()).thenReturn(newPage);

        NotificationRecipientIndex index = this.notificationRecipientIndexManager.getOrBuildIndex("xwiki");
        assertEquals(Set.of(owner), index.findCandidates(oldEvent));

        this.notificationRecipientIndexManager.refreshUser(owner);

        assertTrue(index.findCandidates(oldEvent).isEmpty());
        assertEquals(Set.of(owner), index.findCandidates(newEvent));
    }

    private DefaultNotificationFilterPreference createPreference(long internalId, String owner, String pageOnly)
    {
        DefaultNotificationFilterPreference preference = new DefaultNotificationFilterPreference();
        preference.setInternalId(internalId);
        preference.setOwner(owner);
        preference.setEnabled(true);
        preference.setFilterName("scopeNotificationFilter");
        preference.setFilterType(NotificationFilterType.INCLUSIVE);
        preference.setPageOnly(pageOnly);
        return preference;
    }
}
