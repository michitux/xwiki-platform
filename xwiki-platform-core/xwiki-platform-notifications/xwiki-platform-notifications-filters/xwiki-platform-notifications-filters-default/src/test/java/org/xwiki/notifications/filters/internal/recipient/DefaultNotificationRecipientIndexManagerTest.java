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

import org.junit.jupiter.api.Test;
import org.xwiki.notifications.filters.NotificationFilterType;
import org.xwiki.notifications.filters.internal.DefaultNotificationFilterPreference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private NotificationFilterPreferenceIndexStore notificationFilterPreferenceIndexStore;

    @Test
    void getOrBuildIndexBuildsOnce() throws Exception
    {
        DefaultNotificationFilterPreference preference = new DefaultNotificationFilterPreference();
        preference.setInternalId(12L);
        preference.setOwner("xwiki:XWiki.User");
        preference.setEnabled(true);
        preference.setFilterName("scopeNotificationFilter");
        preference.setFilterType(NotificationFilterType.INCLUSIVE);
        preference.setPageOnly("xwiki:Space.Page");

        when(this.notificationFilterPreferenceIndexStore.loadIndexablePreferencesBatch("xwiki", 0, 100))
            .thenReturn(List.of(preference));
        NotificationRecipientIndex firstIndex = this.notificationRecipientIndexManager.getOrBuildIndex("xwiki");
        NotificationRecipientIndex secondIndex = this.notificationRecipientIndexManager.getOrBuildIndex("xwiki");

        assertSame(firstIndex, secondIndex);
        assertTrue(this.notificationRecipientIndexManager.getIfPresent("xwiki").isPresent());
        verify(this.notificationFilterPreferenceIndexStore, times(1))
            .loadIndexablePreferencesBatch("xwiki", 0, 100);
    }
}
