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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.xwiki.notifications.NotificationFormat;

/**
 * Compact immutable description of an event passed to the notification recipient index.
 *
 * @version $Id$
 * @since 18.4.0
 */
public class NotificationEventDescriptor
{
    private final String wikiId;

    private final String documentReference;

    private final List<String> spaceReferences;

    private final String eventType;

    private final String actor;

    private final Date eventDate;

    private final NotificationFormat format;

    /**
     * @param builder the builder used to create the descriptor
     */
    protected NotificationEventDescriptor(Builder builder)
    {
        this.wikiId = builder.wikiId;
        this.documentReference = builder.documentReference;
        this.spaceReferences = Collections.unmodifiableList(new ArrayList<>(builder.spaceReferences));
        this.eventType = builder.eventType;
        this.actor = builder.actor;
        this.eventDate = builder.eventDate == null ? null : new Date(builder.eventDate.getTime());
        this.format = builder.format;
    }

    /**
     * @return the wiki identifier of the event
     */
    public String getWikiId()
    {
        return this.wikiId;
    }

    /**
     * @return the serialized document reference concerned by the event, if any
     */
    public String getDocumentReference()
    {
        return this.documentReference;
    }

    /**
     * @return the serialized space references concerned by the event, from the closest to the farthest ancestor
     */
    public List<String> getSpaceReferences()
    {
        return this.spaceReferences;
    }

    /**
     * @return the event type
     */
    public String getEventType()
    {
        return this.eventType;
    }

    /**
     * @return the serialized actor reference, if any
     */
    public String getActor()
    {
        return this.actor;
    }

    /**
     * @return the event date, if any
     */
    public Date getEventDate()
    {
        return this.eventDate == null ? null : new Date(this.eventDate.getTime());
    }

    /**
     * @return the notification format, if any
     */
    public NotificationFormat getFormat()
    {
        return this.format;
    }

    /**
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Builder used to create immutable {@link NotificationEventDescriptor} instances.
     */
    public static final class Builder
    {
        private String wikiId;

        private String documentReference;

        private List<String> spaceReferences = new ArrayList<>();

        private String eventType;

        private String actor;

        private Date eventDate;

        private NotificationFormat format;

        /**
         * @param wikiId the wiki identifier
         * @return the current builder
         */
        public Builder wikiId(String wikiId)
        {
            this.wikiId = wikiId;
            return this;
        }

        /**
         * @param documentReference the serialized document reference
         * @return the current builder
         */
        public Builder documentReference(String documentReference)
        {
            this.documentReference = documentReference;
            return this;
        }

        /**
         * @param spaceReferences the serialized space references
         * @return the current builder
         */
        public Builder spaceReferences(List<String> spaceReferences)
        {
            this.spaceReferences = new ArrayList<>(spaceReferences);
            return this;
        }

        /**
         * @param eventType the event type
         * @return the current builder
         */
        public Builder eventType(String eventType)
        {
            this.eventType = eventType;
            return this;
        }

        /**
         * @param actor the serialized actor reference
         * @return the current builder
         */
        public Builder actor(String actor)
        {
            this.actor = actor;
            return this;
        }

        /**
         * @param eventDate the event date
         * @return the current builder
         */
        public Builder eventDate(Date eventDate)
        {
            this.eventDate = eventDate == null ? null : new Date(eventDate.getTime());
            return this;
        }

        /**
         * @param format the notification format
         * @return the current builder
         */
        public Builder format(NotificationFormat format)
        {
            this.format = format;
            return this;
        }

        /**
         * @return the immutable event descriptor
         */
        public NotificationEventDescriptor build()
        {
            return new NotificationEventDescriptor(this);
        }
    }
}
