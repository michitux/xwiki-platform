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
package org.xwiki.rendering.internal.limits;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.job.Job;
import org.xwiki.job.JobContext;
import org.xwiki.rendering.limits.RenderingLimitsProfileResolver;

/**
 * Selects the configuration profile of the rendering limits based on the job the rendering runs in, if any.
 * <p>
 * Rendering a page for a request and rendering it as part of, e.g., a PDF export of a whole space are very different
 * use cases with very different legitimate resource consumptions, and the job type is the signal that distinguishes
 * them. The profiles are the type of the current job with the slashes replaced by dots, e.g. {@code export.pdf}, and
 * then the generic {@code job} profile, so that admins can configure either a single job type or all background work at
 * once.
 * <p>
 * Note that a job doesn't necessarily get budgets of its own: an asynchronous rendering runs as a job, too, but it
 * continues the budgets of the rendering that spawned it, which take precedence over any profile.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Component
@Singleton
public class XWikiRenderingLimitsProfileResolver implements RenderingLimitsProfileResolver
{
    private static final String JOB_PROFILE = "job";

    @Inject
    private JobContext jobContext;

    @Override
    public List<String> getCurrentProfiles()
    {
        Job job = this.jobContext.getCurrentJob();

        if (job == null || job.getType() == null) {
            return List.of();
        }

        return List.of(job.getType().replace('/', '.'), JOB_PROFILE);
    }
}
