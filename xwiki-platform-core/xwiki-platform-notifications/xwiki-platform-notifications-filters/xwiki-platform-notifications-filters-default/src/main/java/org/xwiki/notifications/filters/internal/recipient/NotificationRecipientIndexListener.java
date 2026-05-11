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

import java.util.Collection;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.notifications.filters.internal.event.NotificationFilterPreferenceAddOrUpdatedEvent;
import org.xwiki.notifications.filters.internal.event.NotificationFilterPreferenceDeletedEvent;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

/**
 * Keeps the notification recipient indexes synchronized with preference updates.
 *
 * @version $Id$
 * @since 18.4.0
 */
@Component
@Singleton
@Named(NotificationRecipientIndexListener.NAME)
public class NotificationRecipientIndexListener extends AbstractEventListener
{
    /**
     * The listener name.
     */
    public static final String NAME =
        "org.xwiki.notifications.filters.internal.recipient.NotificationRecipientIndexListener";

    @Inject
    private NotificationRecipientIndexManager notificationRecipientIndexManager;

    /**
     * Default constructor.
     */
    public NotificationRecipientIndexListener()
    {
        super(NAME, new NotificationFilterPreferenceAddOrUpdatedEvent(),
            new NotificationFilterPreferenceDeletedEvent());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        if (event instanceof NotificationFilterPreferenceAddOrUpdatedEvent
            && source instanceof IndexableNotificationFilterPreference preference) {
            addOrUpdatePreference(preference, data);
        } else if (event instanceof NotificationFilterPreferenceDeletedEvent) {
            removePreferences(source, data);
        }
    }

    private void addOrUpdatePreference(IndexableNotificationFilterPreference preference, Object owner)
    {
        String wikiId = getWikiId(owner);
        if (wikiId != null) {
            getDefaultNotificationRecipientIndexManager().addOrUpdatePreference(wikiId, preference);
        }
    }

    private void removePreferences(Object owner, Object data)
    {
        String wikiId = getWikiId(owner);
        Collection<String> preferenceIds = getPreferenceIds(data);
        if (wikiId != null && !preferenceIds.isEmpty()) {
            getDefaultNotificationRecipientIndexManager().removePreferences(wikiId, preferenceIds);
        }
    }

    private DefaultNotificationRecipientIndexManager getDefaultNotificationRecipientIndexManager()
    {
        return (DefaultNotificationRecipientIndexManager) this.notificationRecipientIndexManager;
    }

    private String getWikiId(Object owner)
    {
        if (owner instanceof DocumentReference user) {
            return user.getWikiReference().getName();
        }
        if (owner instanceof WikiReference wikiReference) {
            return wikiReference.getName();
        }
        return null;
    }

    private Collection<String> getPreferenceIds(Object data)
    {
        if (data instanceof String preferenceId) {
            return Set.of(preferenceId);
        }
        if (data instanceof Collection<?> values) {
            return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }
}
