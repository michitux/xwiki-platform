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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.notifications.NotificationException;
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
        try {
            if (event instanceof NotificationFilterPreferenceAddOrUpdatedEvent) {
                refreshOwner(data);
            } else if (event instanceof NotificationFilterPreferenceDeletedEvent) {
                refreshOwner(source);
            }
        } catch (NotificationException e) {
            throw new RuntimeException("Failed to refresh the notification recipient index.", e);
        }
    }

    private void refreshOwner(Object owner) throws NotificationException
    {
        if (owner instanceof DocumentReference user) {
            this.notificationRecipientIndexManager.refreshUser(user);
        } else if (owner instanceof WikiReference wikiReference) {
            this.notificationRecipientIndexManager.refreshWiki(wikiReference);
        }
    }
}
