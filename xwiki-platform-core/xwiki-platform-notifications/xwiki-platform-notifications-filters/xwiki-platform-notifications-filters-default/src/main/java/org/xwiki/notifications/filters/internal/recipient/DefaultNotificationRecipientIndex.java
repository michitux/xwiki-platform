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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.eventstream.Event;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
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

    private final EntityReferenceSerializer<String> entityReferenceSerializer;

    private final DocumentReferenceResolver<String> documentReferenceResolver;

    private final Map<String, IndexedPreference> indexedPreferences = new HashMap<>();

    private final Map<String, Set<String>> indexedPreferencesByOwner = new HashMap<>();

    private final Map<String, Map<String, Set<DocumentReference>>> pageOnlyIndex = new HashMap<>();

    private final Map<String, Map<String, Set<DocumentReference>>> pageIndex = new HashMap<>();

    private final Map<String, Map<String, Set<DocumentReference>>> wikiIndex = new HashMap<>();

    private final Map<String, Set<DocumentReference>> followedUserIndex = new HashMap<>();

    /**
     * @param entityReferenceSerializer the serializer used for event references
     * @param documentReferenceResolver the resolver used for indexed owners
     */
    public DefaultNotificationRecipientIndex(EntityReferenceSerializer<String> entityReferenceSerializer,
        DocumentReferenceResolver<String> documentReferenceResolver)
    {
        this.entityReferenceSerializer = entityReferenceSerializer;
        this.documentReferenceResolver = documentReferenceResolver;
    }

    synchronized void addOrUpdate(IndexableNotificationFilterPreference preference)
    {
        if (StringUtils.isBlank(preference.getId())) {
            return;
        }

        removePreference(preference.getId());

        IndexedPreference indexedPreference = IndexedPreference.create(preference, this.documentReferenceResolver);
        if (indexedPreference == null) {
            return;
        }

        this.indexedPreferences.put(indexedPreference.getPreferenceId(), indexedPreference);
        this.indexedPreferencesByOwner.computeIfAbsent(indexedPreference.getOwnerKey(), ignored -> new HashSet<>())
            .add(indexedPreference.getPreferenceId());
        indexedPreference.addToIndexes(this.pageOnlyIndex, this.pageIndex, this.wikiIndex, this.followedUserIndex);
    }

    synchronized void remove(String preferenceId)
    {
        if (StringUtils.isBlank(preferenceId)) {
            return;
        }

        removePreference(preferenceId);
    }

    synchronized void replaceOwner(String owner,
        Collection<? extends IndexableNotificationFilterPreference> preferences)
    {
        removeOwnerPreferences(owner);
        preferences.forEach(this::addOrUpdate);
    }

    @Override
    public synchronized Set<DocumentReference> findCandidates(Event event)
    {
        Set<DocumentReference> candidateUsers = new LinkedHashSet<>();
        String eventType = event.getType();

        DocumentReference documentReference = event.getDocument();
        if (documentReference != null) {
            String serializedDocumentReference = this.entityReferenceSerializer.serialize(documentReference);
            addCandidates(this.pageOnlyIndex, serializedDocumentReference, eventType, candidateUsers);
            addCandidates(this.pageIndex, serializedDocumentReference, eventType, candidateUsers);

            for (String spaceReference : getSpaceReferences(documentReference)) {
                addCandidates(this.pageIndex, spaceReference, eventType, candidateUsers);
            }
        }

        if (event.getWiki() != null) {
            addCandidates(this.wikiIndex, event.getWiki().getName(), eventType, candidateUsers);
        }

        if (event.getUser() != null) {
            candidateUsers.addAll(this.followedUserIndex.getOrDefault(
                this.entityReferenceSerializer.serialize(event.getUser()), Collections.emptySet()));
        }

        return candidateUsers;
    }

    private void removeOwnerPreferences(String owner)
    {
        Set<String> preferenceIds = this.indexedPreferencesByOwner.get(owner);
        if (preferenceIds == null || preferenceIds.isEmpty()) {
            return;
        }

        for (String preferenceId : Set.copyOf(preferenceIds)) {
            removePreference(preferenceId);
        }
    }

    private void removePreference(String preferenceId)
    {
        IndexedPreference indexedPreference = this.indexedPreferences.remove(preferenceId);
        if (indexedPreference == null) {
            return;
        }

        indexedPreference.removeFromIndexes(this.pageOnlyIndex, this.pageIndex, this.wikiIndex, this.followedUserIndex);

        Set<String> ownerPreferences = this.indexedPreferencesByOwner.get(indexedPreference.getOwnerKey());
        if (ownerPreferences != null) {
            ownerPreferences.remove(preferenceId);
            if (ownerPreferences.isEmpty()) {
                this.indexedPreferencesByOwner.remove(indexedPreference.getOwnerKey());
            }
        }
    }

    private void addCandidates(Map<String, Map<String, Set<DocumentReference>>> index, String key, String eventType,
        Set<DocumentReference> collector)
    {
        Map<String, Set<DocumentReference>> eventTypeIndex = index.get(key);
        if (eventTypeIndex == null) {
            return;
        }

        addCandidateOwners(eventTypeIndex.get(ALL_EVENT_TYPES), collector);
        if (StringUtils.isNotBlank(eventType)) {
            addCandidateOwners(eventTypeIndex.get(eventType), collector);
        }
    }

    private void addCandidateOwners(Collection<DocumentReference> owners, Set<DocumentReference> collector)
    {
        if (owners != null && !owners.isEmpty()) {
            collector.addAll(owners);
        }
    }

    private Set<String> getSpaceReferences(DocumentReference documentReference)
    {
        Set<String> result = new LinkedHashSet<>();
        EntityReference current = documentReference.getParent();
        while (current != null && current.getType() == EntityType.SPACE) {
            result.add(this.entityReferenceSerializer.serialize(current));
            current = current.getParent();
        }
        return result;
    }

    private static final class IndexedPreference
    {
        private final String preferenceId;

        private final String ownerKey;

        private final DocumentReference owner;

        private final IndexedPreferenceType type;

        private final String key;

        private final Set<String> eventTypes;

        private IndexedPreference(String preferenceId, String ownerKey, DocumentReference owner,
            IndexedPreferenceType type,
            String key, Set<String> eventTypes)
        {
            this.preferenceId = preferenceId;
            this.ownerKey = ownerKey;
            this.owner = owner;
            this.type = type;
            this.key = key;
            this.eventTypes = eventTypes;
        }

        private static IndexedPreference create(IndexableNotificationFilterPreference preference,
            DocumentReferenceResolver<String> documentReferenceResolver)
        {
            if (!preference.isEnabled() || preference.getFilterType() != NotificationFilterType.INCLUSIVE
                || StringUtils.isBlank(preference.getId())) {
                return null;
            }

            DocumentReference owner = resolveOwner(preference, documentReferenceResolver);
            if (owner == null) {
                return null;
            }

            if (ScopeNotificationFilter.FILTER_NAME.equals(preference.getFilterName())) {
                return createScopePreference(preference, owner);
            }

            if (EventUserFilter.FILTER_NAME.equals(preference.getFilterName())
                && StringUtils.isNotBlank(preference.getUser())) {
                return new IndexedPreference(preference.getId(), preference.getOwner(), owner,
                    IndexedPreferenceType.FOLLOWED_USER, preference.getUser(), Collections.singleton(ALL_EVENT_TYPES));
            }

            return null;
        }

        private static DocumentReference resolveOwner(IndexableNotificationFilterPreference preference,
            DocumentReferenceResolver<String> documentReferenceResolver)
        {
            String owner = preference.getOwner();
            if (StringUtils.isBlank(owner) || !StringUtils.contains(owner, ':')) {
                return null;
            }
            return documentReferenceResolver.resolve(owner);
        }

        private static IndexedPreference createScopePreference(IndexableNotificationFilterPreference preference,
            DocumentReference owner)
        {
            IndexedPreferenceType indexedPreferenceType = null;
            String key = null;

            if (StringUtils.isNotBlank(preference.getPageOnly())) {
                indexedPreferenceType = IndexedPreferenceType.PAGE_ONLY;
                key = preference.getPageOnly();
            } else if (StringUtils.isNotBlank(preference.getPage())) {
                indexedPreferenceType = IndexedPreferenceType.PAGE;
                key = preference.getPage();
            } else if (StringUtils.isNotBlank(preference.getWiki())) {
                indexedPreferenceType = IndexedPreferenceType.WIKI;
                key = preference.getWiki();
            }

            if (indexedPreferenceType == null) {
                return null;
            }

            return new IndexedPreference(preference.getId(), preference.getOwner(), owner,
                indexedPreferenceType, key, getEventTypeKeys(preference));
        }

        private String getPreferenceId()
        {
            return this.preferenceId;
        }

        private String getOwnerKey()
        {
            return this.ownerKey;
        }

        private void addToIndexes(Map<String, Map<String, Set<DocumentReference>>> pageOnlyIndex,
            Map<String, Map<String, Set<DocumentReference>>> pageIndex,
            Map<String, Map<String, Set<DocumentReference>>> wikiIndex,
            Map<String, Set<DocumentReference>> followedUserIndex)
        {
            if (this.type == IndexedPreferenceType.FOLLOWED_USER) {
                followedUserIndex.computeIfAbsent(this.key, ignored -> new HashSet<>()).add(this.owner);
                return;
            }

            Map<String, Map<String, Set<DocumentReference>>> index = getIndex(pageOnlyIndex, pageIndex, wikiIndex);
            Map<String, Set<DocumentReference>> eventTypeIndex = index.computeIfAbsent(this.key,
                ignored -> new HashMap<>());
            for (String eventType : this.eventTypes) {
                eventTypeIndex.computeIfAbsent(eventType, ignored -> new HashSet<>()).add(this.owner);
            }
        }

        private void removeFromIndexes(Map<String, Map<String, Set<DocumentReference>>> pageOnlyIndex,
            Map<String, Map<String, Set<DocumentReference>>> pageIndex,
            Map<String, Map<String, Set<DocumentReference>>> wikiIndex,
            Map<String, Set<DocumentReference>> followedUserIndex)
        {
            if (this.type == IndexedPreferenceType.FOLLOWED_USER) {
                removeFromFollowedUserIndex(followedUserIndex);
                return;
            }

            removeFromLocationIndex(getIndex(pageOnlyIndex, pageIndex, wikiIndex));
        }

        private Map<String, Map<String, Set<DocumentReference>>> getIndex(
            Map<String, Map<String, Set<DocumentReference>>> pageOnlyIndex,
            Map<String, Map<String, Set<DocumentReference>>> pageIndex,
            Map<String, Map<String, Set<DocumentReference>>> wikiIndex)
        {
            return switch (this.type) {
                case PAGE_ONLY -> pageOnlyIndex;
                case PAGE -> pageIndex;
                case WIKI -> wikiIndex;
                case FOLLOWED_USER ->
                    throw new IllegalStateException(String.format("Unsupported location index type [%s].", this.type));
            };
        }

        private void removeFromLocationIndex(Map<String, Map<String, Set<DocumentReference>>> index)
        {
            Map<String, Set<DocumentReference>> eventTypeIndex = index.get(this.key);
            if (eventTypeIndex == null) {
                return;
            }

            for (String eventType : this.eventTypes) {
                Set<DocumentReference> owners = eventTypeIndex.get(eventType);
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

        private void removeFromFollowedUserIndex(Map<String, Set<DocumentReference>> followedUserIndex)
        {
            Set<DocumentReference> owners = followedUserIndex.get(this.key);
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
        WIKI,
        FOLLOWED_USER
    }
}
