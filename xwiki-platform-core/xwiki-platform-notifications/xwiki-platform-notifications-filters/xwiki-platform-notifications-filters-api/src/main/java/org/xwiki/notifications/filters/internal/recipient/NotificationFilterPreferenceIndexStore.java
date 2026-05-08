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
import java.util.Optional;

import org.xwiki.component.annotation.Role;
import org.xwiki.notifications.NotificationException;

/**
 * Store API used by the notification recipient index.
 *
 * @version $Id$
 * @since 18.4.0
 */
@Role
public interface NotificationFilterPreferenceIndexStore
{
    /**
     * Load a batch of preferences from the specified storage wiki.
     *
     * @param wikiId the wiki database to scan
     * @param afterInternalId only preferences with a greater internal id are returned
     * @param limit the maximum number of preferences to return
     * @return a batch of preferences ordered by internal id
     * @throws NotificationException in case of error while loading the preferences
     */
    List<IndexableNotificationFilterPreference> loadIndexablePreferencesBatch(String wikiId, long afterInternalId,
        int limit) throws NotificationException;

    /**
     * Load all preferences owned by the specified owner.
     *
     * @param owner the serialized owner reference
     * @return the preferences owned by the specified owner
     * @throws NotificationException in case of error while loading the preferences
     */
    List<IndexableNotificationFilterPreference> loadIndexablePreferencesForOwner(String owner)
        throws NotificationException;

    /**
     * Load a specific preference from the specified storage wiki.
     *
     * @param wikiId the wiki database to scan
     * @param preferenceId the public preference identifier
     * @return the corresponding preference, if any
     * @throws NotificationException in case of error while loading the preference
     */
    Optional<IndexableNotificationFilterPreference> loadIndexablePreferenceById(String wikiId, String preferenceId)
        throws NotificationException;
}
