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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.notifications.filters.NotificationFilterPreference;
import org.xwiki.notifications.filters.NotificationFilterType;
import org.xwiki.notifications.filters.internal.scope.ScopeNotificationFilter;
import org.xwiki.notifications.filters.internal.user.EventUserFilter;

/**
 * Default in-memory implementation of {@link NotificationRecipientIndex}.
 *
 * @version $Id$
 * @since 18.4.0
 */
public class DefaultNotificationRecipientIndex implements NotificationRecipientIndex
{
    static final String ALL_EVENT_TYPES = "__all__";

    private final Map<Long, IndexedPreference> indexedPreferences = new HashMap<>();

    private final Map<String, Map<String, Set<String>>> pageOnlyIndex = new HashMap<>();

    private final Map<String, Map<String, Set<String>>> pageIndex = new HashMap<>();

    private final Map<String, Map<String, Set<String>>> spaceIndex = new HashMap<>();

    private final Map<String, Map<String, Set<String>>> wikiIndex = new HashMap<>();

    private final Map<String, Set<String>> followedUserIndex = new HashMap<>();

    @Override
    public synchronized void addOrUpdate(IndexableNotificationFilterPreference preference)
    {
        removeInternalPreference(preference.getInternalId());

        IndexedPreference indexedPreference = IndexedPreference.create(preference);
        if (indexedPreference == null) {
            return;
        }

        this.indexedPreferences.put(indexedPreference.getInternalId(), indexedPreference);
        indexedPreference.addToIndexes(this.pageOnlyIndex, this.pageIndex, this.spaceIndex, this.wikiIndex,
            this.followedUserIndex);
    }

    @Override
    public synchronized void remove(IndexableNotificationFilterPreference preference)
    {
        removeInternalPreference(preference.getInternalId());
    }

    @Override
    public synchronized void remove(String preferenceId)
    {
        if (StringUtils.startsWith(preferenceId, NotificationFilterPreference.DB_ID_FILTER_PREFIX)) {
            long internalId = Long.parseLong(
                preferenceId.substring(NotificationFilterPreference.DB_ID_FILTER_PREFIX.length()));
            removeInternalPreference(internalId);
        }
    }

    @Override
    public synchronized NotificationCandidateSet findCandidates(NotificationEventDescriptor eventDescriptor)
    {
        NotificationCandidateSet candidateSet = new NotificationCandidateSet();

        if (StringUtils.isNotBlank(eventDescriptor.getDocumentReference())) {
            addCandidates(this.pageOnlyIndex, eventDescriptor.getDocumentReference(), eventDescriptor.getEventType(),
                candidateSet::addScopeCandidateUser);
            addCandidates(this.pageIndex, eventDescriptor.getDocumentReference(), eventDescriptor.getEventType(),
                candidateSet::addScopeCandidateUser);
        }

        for (String spaceReference : eventDescriptor.getSpaceReferences()) {
            addCandidates(this.pageIndex, spaceReference, eventDescriptor.getEventType(),
                candidateSet::addScopeCandidateUser);
            addCandidates(this.spaceIndex, spaceReference, eventDescriptor.getEventType(),
                candidateSet::addScopeCandidateUser);
        }

        if (StringUtils.isNotBlank(eventDescriptor.getWikiId())) {
            if (addCandidates(this.wikiIndex, eventDescriptor.getWikiId(), eventDescriptor.getEventType(),
                candidateSet::addScopeCandidateUser)) {
                candidateSet.setBroadWikiMatch(true);
            }
        }

        if (StringUtils.isNotBlank(eventDescriptor.getActor())) {
            for (String owner : this.followedUserIndex.getOrDefault(eventDescriptor.getActor(),
                Collections.emptySet())) {
                candidateSet.addFollowedUserCandidateUser(owner);
            }
        }

        return candidateSet;
    }

    private void removeInternalPreference(long internalId)
    {
        IndexedPreference indexedPreference = this.indexedPreferences.remove(internalId);
        if (indexedPreference != null) {
            indexedPreference.removeFromIndexes(this.pageOnlyIndex, this.pageIndex, this.spaceIndex, this.wikiIndex,
                this.followedUserIndex);
        }
    }

    private boolean addCandidates(Map<String, Map<String, Set<String>>> index, String key, String eventType,
        CandidateCollector collector)
    {
        Map<String, Set<String>> eventTypeIndex = index.get(key);
        if (eventTypeIndex == null) {
            return false;
        }

        boolean found = false;
        found |= addCandidateOwners(eventTypeIndex.get(ALL_EVENT_TYPES), collector);
        if (StringUtils.isNotBlank(eventType)) {
            found |= addCandidateOwners(eventTypeIndex.get(eventType), collector);
        }
        return found;
    }

    private boolean addCandidateOwners(Collection<String> owners, CandidateCollector collector)
    {
        if (owners == null || owners.isEmpty()) {
            return false;
        }

        owners.forEach(collector::collect);
        return true;
    }

    @FunctionalInterface
    private interface CandidateCollector
    {
        void collect(String owner);
    }

    private static final class IndexedPreference
    {
        private final long internalId;

        private final String owner;

        private final IndexedPreferenceType type;

        private final String key;

        private final Set<String> eventTypes;

        private IndexedPreference(long internalId, String owner, IndexedPreferenceType type, String key,
            Set<String> eventTypes)
        {
            this.internalId = internalId;
            this.owner = owner;
            this.type = type;
            this.key = key;
            this.eventTypes = eventTypes;
        }

        private static IndexedPreference create(IndexableNotificationFilterPreference preference)
        {
            if (!preference.isEnabled() || preference.getFilterType() != NotificationFilterType.INCLUSIVE) {
                return null;
            }

            if (ScopeNotificationFilter.FILTER_NAME.equals(preference.getFilterName())) {
                if (StringUtils.isNotBlank(preference.getPageOnly())) {
                    return new IndexedPreference(preference.getInternalId(), preference.getOwner(),
                        IndexedPreferenceType.PAGE_ONLY, preference.getPageOnly(), getEventTypeKeys(preference));
                }
                if (StringUtils.isNotBlank(preference.getPage())) {
                    return new IndexedPreference(preference.getInternalId(), preference.getOwner(),
                        IndexedPreferenceType.PAGE, preference.getPage(), getEventTypeKeys(preference));
                }
                if (StringUtils.isNotBlank(preference.getWiki())) {
                    return new IndexedPreference(preference.getInternalId(), preference.getOwner(),
                        IndexedPreferenceType.WIKI, preference.getWiki(), getEventTypeKeys(preference));
                }
            }

            if (EventUserFilter.FILTER_NAME.equals(preference.getFilterName())
                && StringUtils.isNotBlank(preference.getUser())) {
                return new IndexedPreference(preference.getInternalId(), preference.getOwner(),
                    IndexedPreferenceType.FOLLOWED_USER, preference.getUser(), Collections.singleton(ALL_EVENT_TYPES));
            }

            return null;
        }

        private long getInternalId()
        {
            return this.internalId;
        }

        private void addToIndexes(Map<String, Map<String, Set<String>>> pageOnlyIndex,
            Map<String, Map<String, Set<String>>> pageIndex, Map<String, Map<String, Set<String>>> spaceIndex,
            Map<String, Map<String, Set<String>>> wikiIndex, Map<String, Set<String>> followedUserIndex)
        {
            if (this.type == IndexedPreferenceType.FOLLOWED_USER) {
                followedUserIndex.computeIfAbsent(this.key, ignored -> new HashSet<>()).add(this.owner);
                return;
            }

            Map<String, Map<String, Set<String>>> index = getIndex(pageOnlyIndex, pageIndex, spaceIndex, wikiIndex);
            Map<String, Set<String>> eventTypeIndex = index.computeIfAbsent(this.key, ignored -> new HashMap<>());
            for (String eventType : this.eventTypes) {
                eventTypeIndex.computeIfAbsent(eventType, ignored -> new HashSet<>()).add(this.owner);
            }
        }

        private void removeFromIndexes(Map<String, Map<String, Set<String>>> pageOnlyIndex,
            Map<String, Map<String, Set<String>>> pageIndex, Map<String, Map<String, Set<String>>> spaceIndex,
            Map<String, Map<String, Set<String>>> wikiIndex, Map<String, Set<String>> followedUserIndex)
        {
            if (this.type == IndexedPreferenceType.FOLLOWED_USER) {
                removeFromFollowedUserIndex(followedUserIndex);
                return;
            }

            removeFromLocationIndex(getIndex(pageOnlyIndex, pageIndex, spaceIndex, wikiIndex));
        }

        private Map<String, Map<String, Set<String>>> getIndex(Map<String, Map<String, Set<String>>> pageOnlyIndex,
            Map<String, Map<String, Set<String>>> pageIndex, Map<String, Map<String, Set<String>>> spaceIndex,
            Map<String, Map<String, Set<String>>> wikiIndex)
        {
            return switch (this.type) {
                case PAGE_ONLY -> pageOnlyIndex;
                case PAGE -> pageIndex;
                case SPACE -> spaceIndex;
                case WIKI -> wikiIndex;
                case FOLLOWED_USER -> throw new IllegalStateException("Unsupported location index type.");
            };
        }

        private void removeFromLocationIndex(Map<String, Map<String, Set<String>>> index)
        {
            Map<String, Set<String>> eventTypeIndex = index.get(this.key);
            if (eventTypeIndex == null) {
                return;
            }

            for (String eventType : this.eventTypes) {
                Set<String> owners = eventTypeIndex.get(eventType);
                if (owners != null) {
                    owners.remove(this.owner);
                    if (owners.isEmpty()) {
                        eventTypeIndex.remove(eventType);
                    }
                }
            }

            if (eventTypeIndex.isEmpty()) {
                index.remove(this.key);
            }
        }

        private void removeFromFollowedUserIndex(Map<String, Set<String>> followedUserIndex)
        {
            Set<String> owners = followedUserIndex.get(this.key);
            if (owners != null) {
                owners.remove(this.owner);
                if (owners.isEmpty()) {
                    followedUserIndex.remove(this.key);
                }
            }
        }

        private static Set<String> getEventTypeKeys(IndexableNotificationFilterPreference preference)
        {
            if (preference.getEventTypes().isEmpty()) {
                return Collections.singleton(ALL_EVENT_TYPES);
            }
            return new HashSet<>(preference.getEventTypes());
        }
    }

    private enum IndexedPreferenceType
    {
        PAGE_ONLY,
        PAGE,
        SPACE,
        WIKI,
        FOLLOWED_USER
    }
}
