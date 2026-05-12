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
    /**
     * Synthetic event type bucket used for preferences that are not restricted to specific event types.
     */
    static final String ALL_EVENT_TYPES = "__all__";

    private final EntityReferenceSerializer<String> entityReferenceSerializer;

    private final DocumentReferenceResolver<String> documentReferenceResolver;

    /**
     * Stores the indexed representation of each preference so updates and removals can undo the previous indexing
     * without rebuilding the whole structure.
     */
    private final Map<String, IndexedPreference> indexedPreferences = new HashMap<>();

    /**
     * Location-based indexes are keyed by location first and by event type second. Page-only entries match the exact
     * page, page entries match a page or one of its ancestor spaces, and wiki entries match the wiki id.
     */
    private final Map<String, Map<String, Set<DocumentReference>>> pageOnlyIndex = new HashMap<>();

    private final Map<String, Map<String, Set<DocumentReference>>> pageIndex = new HashMap<>();

    private final Map<String, Map<String, Set<DocumentReference>>> wikiIndex = new HashMap<>();

    /**
     * Followed-user preferences match all event types, so they only need to be keyed by the serialized followed user.
     */
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

        IndexedPreference indexedPreference = createIndexedPreference(preference);
        if (indexedPreference == null) {
            return;
        }

        this.indexedPreferences.put(indexedPreference.preferenceId(), indexedPreference);
        addToIndexes(indexedPreference);
    }

    synchronized void remove(String preferenceId)
    {
        if (StringUtils.isBlank(preferenceId)) {
            return;
        }

        removePreference(preferenceId);
    }

    @Override
    public synchronized Set<DocumentReference> findCandidates(Event event)
    {
        Set<DocumentReference> candidateUsers = new LinkedHashSet<>();
        String eventType = event.getType();

        DocumentReference documentReference = event.getDocument();
        if (documentReference != null) {
            addDocumentCandidates(documentReference, eventType, candidateUsers);
        }

        if (event.getWiki() != null) {
            addCandidates(this.wikiIndex, event.getWiki().getName(), eventType, candidateUsers);
        }

        if (event.getUser() != null) {
            addFollowedUserCandidates(event.getUser(), candidateUsers);
        }

        return candidateUsers;
    }

    private void removePreference(String preferenceId)
    {
        IndexedPreference indexedPreference = this.indexedPreferences.remove(preferenceId);
        if (indexedPreference == null) {
            return;
        }

        removeFromIndexes(indexedPreference);
    }

    private IndexedPreference createIndexedPreference(IndexableNotificationFilterPreference preference)
    {
        if (!preference.isEnabled() || preference.getFilterType() != NotificationFilterType.INCLUSIVE
            || StringUtils.isBlank(preference.getId())) {
            return null;
        }

        DocumentReference owner = resolveOwner(preference);
        if (owner == null) {
            return null;
        }

        if (ScopeNotificationFilter.FILTER_NAME.equals(preference.getFilterName())) {
            return createScopePreference(preference, owner);
        }

        if (EventUserFilter.FILTER_NAME.equals(preference.getFilterName())
            && StringUtils.isNotBlank(preference.getUser())) {
            return new IndexedPreference(preference.getId(), owner, IndexedPreferenceType.FOLLOWED_USER,
                preference.getUser(), Set.of(ALL_EVENT_TYPES));
        }

        return null;
    }

    private DocumentReference resolveOwner(IndexableNotificationFilterPreference preference)
    {
        String owner = preference.getOwner();
        if (StringUtils.isBlank(owner) || !StringUtils.contains(owner, ':')) {
            return null;
        }
        return this.documentReferenceResolver.resolve(owner);
    }

    private IndexedPreference createScopePreference(IndexableNotificationFilterPreference preference,
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

        return new IndexedPreference(preference.getId(), owner, indexedPreferenceType, key, getIndexedEventTypes(
            preference));
    }

    private void addToIndexes(IndexedPreference indexedPreference)
    {
        if (indexedPreference.type() == IndexedPreferenceType.FOLLOWED_USER) {
            this.followedUserIndex.computeIfAbsent(indexedPreference.key(), ignored -> new HashSet<>())
                .add(indexedPreference.owner());
            return;
        }

        Map<String, Set<DocumentReference>> eventTypeIndex =
            getLocationIndex(indexedPreference.type()).computeIfAbsent(indexedPreference.key(),
                ignored -> new HashMap<>());
        for (String eventType : indexedPreference.eventTypes()) {
            eventTypeIndex.computeIfAbsent(eventType, ignored -> new HashSet<>()).add(indexedPreference.owner());
        }
    }

    private void removeFromIndexes(IndexedPreference indexedPreference)
    {
        if (indexedPreference.type() == IndexedPreferenceType.FOLLOWED_USER) {
            Set<DocumentReference> owners = this.followedUserIndex.get(indexedPreference.key());
            if (owners != null) {
                owners.remove(indexedPreference.owner());
                if (owners.isEmpty()) {
                    this.followedUserIndex.remove(indexedPreference.key());
                }
            }
            return;
        }

        Map<String, Map<String, Set<DocumentReference>>> index = getLocationIndex(indexedPreference.type());
        Map<String, Set<DocumentReference>> eventTypeIndex = index.get(indexedPreference.key());
        if (eventTypeIndex == null) {
            return;
        }

        for (String eventType : indexedPreference.eventTypes()) {
            Set<DocumentReference> owners = eventTypeIndex.get(eventType);
            if (owners != null) {
                owners.remove(indexedPreference.owner());
                if (owners.isEmpty()) {
                    eventTypeIndex.remove(eventType);
                }
            }
        }

        if (eventTypeIndex.isEmpty()) {
            index.remove(indexedPreference.key());
        }
    }

    private Map<String, Map<String, Set<DocumentReference>>> getLocationIndex(IndexedPreferenceType type)
    {
        return switch (type) {
            case PAGE_ONLY -> this.pageOnlyIndex;
            case PAGE -> this.pageIndex;
            case WIKI -> this.wikiIndex;
            case FOLLOWED_USER ->
                throw new IllegalStateException(String.format("Unsupported location index type [%s].", type));
        };
    }

    private void addDocumentCandidates(DocumentReference documentReference, String eventType,
        Set<DocumentReference> candidateUsers)
    {
        String serializedDocumentReference = serialize(documentReference);

        // Exact page watchers are distinct from page/space watchers because they should not match ancestor spaces.
        addCandidates(this.pageOnlyIndex, serializedDocumentReference, eventType, candidateUsers);
        addCandidates(this.pageIndex, serializedDocumentReference, eventType, candidateUsers);

        // Page-scope preferences also match the spaces containing the modified page.
        for (String spaceReference : getAncestorSpaceReferences(documentReference)) {
            addCandidates(this.pageIndex, spaceReference, eventType, candidateUsers);
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

    private void addFollowedUserCandidates(DocumentReference user, Set<DocumentReference> collector)
    {
        collector.addAll(this.followedUserIndex.getOrDefault(serialize(user), Set.of()));
    }

    private String serialize(EntityReference reference)
    {
        return this.entityReferenceSerializer.serialize(reference);
    }

    private Set<String> getAncestorSpaceReferences(DocumentReference documentReference)
    {
        Set<String> result = new LinkedHashSet<>();
        EntityReference current = documentReference.getParent();
        while (current != null && current.getType() == EntityType.SPACE) {
            result.add(serialize(current));
            current = current.getParent();
        }
        return result;
    }

    private Set<String> getIndexedEventTypes(IndexableNotificationFilterPreference preference)
    {
        if (preference.getEventTypes().isEmpty()) {
            return Set.of(ALL_EVENT_TYPES);
        }
        return new HashSet<>(preference.getEventTypes());
    }

    /**
     * Immutable description of a preference once translated to the keys used by the in-memory indexes.
     */
    private record IndexedPreference(String preferenceId, DocumentReference owner, IndexedPreferenceType type,
        String key, Set<String> eventTypes)
    {
        private IndexedPreference
        {
            eventTypes = Set.copyOf(eventTypes);
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
