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

import java.util.LinkedHashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.notifications.NotificationException;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

/**
 * Default implementation of {@link NotificationRecipientResolver}.
 *
 * @version $Id$
 * @since 18.4.0
 */
@Component
@Singleton
public class DefaultNotificationRecipientResolver implements NotificationRecipientResolver
{
    @Inject
    private NotificationRecipientIndexManager notificationRecipientIndexManager;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Override
    public Set<String> resolveCandidateUsers(NotificationEventDescriptor eventDescriptor) throws NotificationException
    {
        Set<String> candidateUsers = new LinkedHashSet<>(findCandidates(eventDescriptor.getWikiId(), eventDescriptor));

        String mainWikiId = this.wikiDescriptorManager.getMainWikiId();
        if (!mainWikiId.equals(eventDescriptor.getWikiId())) {
            candidateUsers.addAll(findCandidates(mainWikiId, eventDescriptor));
        }

        return candidateUsers;
    }

    private Set<String> findCandidates(String storeWikiId, NotificationEventDescriptor eventDescriptor)
        throws NotificationException
    {
        NotificationCandidateSet candidateSet =
            this.notificationRecipientIndexManager.getOrBuildIndex(storeWikiId).findCandidates(eventDescriptor);
        return candidateSet.getCandidateUsers();
    }
}
