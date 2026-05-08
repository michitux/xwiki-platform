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

import java.util.Set;

import org.xwiki.component.annotation.Role;
import org.xwiki.notifications.NotificationException;

/**
 * Resolves candidate recipients for a notification event.
 *
 * @version $Id$
 * @since 18.4.0
 */
@Role
public interface NotificationRecipientResolver
{
    /**
     * Resolve the candidate owners that may receive a notification for the given event.
     *
     * @param eventDescriptor the event to evaluate
     * @return the candidate owners
     * @throws NotificationException in case of error while resolving the candidates
     */
    Set<String> resolveCandidateUsers(NotificationEventDescriptor eventDescriptor) throws NotificationException;
}
