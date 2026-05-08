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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Candidate owners returned by the notification recipient index.
 *
 * @version $Id$
 * @since 18.4.0
 */
public class NotificationCandidateSet
{
    private final Set<String> scopeCandidateUsers = new LinkedHashSet<>();

    private final Set<String> followedUserCandidateUsers = new LinkedHashSet<>();

    private boolean broadWikiMatch;

    /**
     * @param user the serialized owner reference to add
     */
    public void addScopeCandidateUser(String user)
    {
        this.scopeCandidateUsers.add(user);
    }

    /**
     * @param user the serialized owner reference to add
     */
    public void addFollowedUserCandidateUser(String user)
    {
        this.followedUserCandidateUsers.add(user);
    }

    /**
     * @return the owners selected by scope filters
     */
    public Set<String> getScopeCandidateUsers()
    {
        return Collections.unmodifiableSet(this.scopeCandidateUsers);
    }

    /**
     * @return the owners selected by followed-user filters
     */
    public Set<String> getFollowedUserCandidateUsers()
    {
        return Collections.unmodifiableSet(this.followedUserCandidateUsers);
    }

    /**
     * @return all candidate owners
     */
    public Set<String> getCandidateUsers()
    {
        LinkedHashSet<String> result = new LinkedHashSet<>(this.scopeCandidateUsers);
        result.addAll(this.followedUserCandidateUsers);
        return result;
    }

    /**
     * @return {@code true} when at least one wiki-wide scope filter matched the event
     */
    public boolean hasBroadWikiMatch()
    {
        return this.broadWikiMatch;
    }

    /**
     * @param broadWikiMatch whether a wiki-wide match has been found
     */
    public void setBroadWikiMatch(boolean broadWikiMatch)
    {
        this.broadWikiMatch = broadWikiMatch;
    }
}
