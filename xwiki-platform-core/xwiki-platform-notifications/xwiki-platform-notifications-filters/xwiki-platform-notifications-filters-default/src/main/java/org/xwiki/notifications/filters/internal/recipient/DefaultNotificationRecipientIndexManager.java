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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.notifications.NotificationException;

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
    private NotificationFilterPreferenceIndexStore indexStore;

    private final ConcurrentMap<String, NotificationRecipientIndex> indexes = new ConcurrentHashMap<>();

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

            NotificationRecipientIndex createdIndex = buildIndex(wikiId);
            this.indexes.put(wikiId, createdIndex);
            return createdIndex;
        }
    }

    @Override
    public Optional<NotificationRecipientIndex> getIfPresent(String wikiId)
    {
        return Optional.ofNullable(this.indexes.get(wikiId));
    }

    @Override
    public void invalidateWiki(String wikiId)
    {
        this.indexes.remove(wikiId);
    }

    @Override
    public void clear()
    {
        this.indexes.clear();
    }

    private NotificationRecipientIndex buildIndex(String wikiId) throws NotificationException
    {
        DefaultNotificationRecipientIndex index = new DefaultNotificationRecipientIndex();

        long afterInternalId = 0;
        while (true) {
            List<IndexableNotificationFilterPreference> batch =
                this.indexStore.loadIndexablePreferencesBatch(wikiId, afterInternalId, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            batch.forEach(index::addOrUpdate);

            long nextAfterInternalId = batch.stream().mapToLong(IndexableNotificationFilterPreference::getInternalId)
                .max().orElse(afterInternalId);
            if (nextAfterInternalId <= afterInternalId || batch.size() < BATCH_SIZE) {
                break;
            }
            afterInternalId = nextAfterInternalId;
        }

        return index;
    }
}
