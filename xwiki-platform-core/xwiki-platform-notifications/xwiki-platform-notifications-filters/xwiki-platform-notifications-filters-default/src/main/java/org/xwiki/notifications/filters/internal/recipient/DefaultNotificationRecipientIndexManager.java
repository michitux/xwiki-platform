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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.notifications.NotificationException;
import org.xwiki.notifications.filters.internal.DefaultNotificationFilterPreference;
import org.xwiki.notifications.filters.internal.NotificationFilterPreferenceStore;

/**
 * Default implementation of {@link NotificationRecipientIndexManager}.
 *
 * @version $Id$
 * @since 18.4.0
 */
@Component
@Singleton
public class DefaultNotificationRecipientIndexManager implements NotificationRecipientIndexManager
{
    private static final int BATCH_SIZE = 100;

    @Inject
    private NotificationFilterPreferenceStore notificationFilterPreferenceStore;

    @Inject
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    @Inject
    private DocumentReferenceResolver<String> documentReferenceResolver;

    private final ConcurrentMap<String, DefaultNotificationRecipientIndex> indexes = new ConcurrentHashMap<>();

    @Override
    public NotificationRecipientIndex getOrBuildIndex(String wikiId) throws NotificationException
    {
        NotificationRecipientIndex existingIndex = this.indexes.get(wikiId);
        if (existingIndex != null) {
            return existingIndex;
        }

        synchronized (this.indexes) {
            NotificationRecipientIndex synchronizedIndex = this.indexes.get(wikiId);
            if (synchronizedIndex != null) {
                return synchronizedIndex;
            }

            DefaultNotificationRecipientIndex createdIndex = buildIndex(wikiId);
            this.indexes.put(wikiId, createdIndex);
            return createdIndex;
        }
    }

    void addOrUpdatePreference(String wikiId, IndexableNotificationFilterPreference preference)
    {
        DefaultNotificationRecipientIndex index = this.indexes.get(wikiId);
        if (index != null) {
            index.addOrUpdate(preference);
        }
    }

    void removePreferences(String wikiId, Collection<String> preferenceIds)
    {
        DefaultNotificationRecipientIndex index = this.indexes.get(wikiId);
        if (index != null) {
            preferenceIds.forEach(index::remove);
        }
    }

    private DefaultNotificationRecipientIndex buildIndex(String wikiId) throws NotificationException
    {
        DefaultNotificationRecipientIndex index =
            new DefaultNotificationRecipientIndex(this.entityReferenceSerializer, this.documentReferenceResolver);

        long afterInternalId = 0;
        while (true) {
            List<DefaultNotificationFilterPreference> batch =
                this.notificationFilterPreferenceStore.loadIndexablePreferencesBatch(wikiId, afterInternalId,
                    BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            batch.forEach(index::addOrUpdate);

            long nextAfterInternalId = batch.stream().mapToLong(DefaultNotificationFilterPreference::getInternalId)
                .max().orElse(afterInternalId);
            if (nextAfterInternalId <= afterInternalId || batch.size() < BATCH_SIZE) {
                break;
            }
            afterInternalId = nextAfterInternalId;
        }

        return index;
    }
}
