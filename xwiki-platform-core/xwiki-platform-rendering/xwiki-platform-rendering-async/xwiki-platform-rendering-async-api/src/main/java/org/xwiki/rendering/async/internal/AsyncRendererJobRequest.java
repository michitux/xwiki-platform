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
package org.xwiki.rendering.async.internal;

import org.xwiki.job.AbstractRequest;
import org.xwiki.job.JobGroupPath;
import org.xwiki.job.Request;
import org.xwiki.rendering.limits.RenderingLimitsSnapshot;

/**
 * The request of the asynchronous renderer job.
 * 
 * @version $Id$
 * @since 10.10RC1
 */
public class AsyncRendererJobRequest extends AbstractRequest
{
    private transient AsyncRenderer renderer;

    private JobGroupPath jobGroupPath;

    /**
     * Transient as a snapshot isn't serializable, and it isn't part of the request's identity either: it is only used
     * to hand the limits over to the thread executing the job.
     */
    private transient RenderingLimitsSnapshot limitsSnapshot;

    /**
     * The default constructor.
     */
    public AsyncRendererJobRequest()
    {
        setVerbose(false);
    }

    /**
     * @param request the request to copy
     */
    public AsyncRendererJobRequest(Request request)
    {
        super(request);

        setVerbose(false);
    }

    /**
     * @return the renderer to execute
     */
    public AsyncRenderer getRenderer()
    {
        return this.renderer;
    }

    /**
     * @param renderer the renderer to execute
     */
    public void setRenderer(AsyncRenderer renderer)
    {
        this.renderer = renderer;
    }

    /**
     * Set the {@link JobGroupPath} to use for the AsyncRendererJob. If this path is not specified the single job
     * pool will be used.
     * @param jobGroupPath the path to be used for the GroupedJob.
     */
    public void setJobGroupPath(JobGroupPath jobGroupPath)
    {
        this.jobGroupPath = jobGroupPath;
    }

    /**
     * @return the {@link JobGroupPath} to be used for the {@link org.xwiki.job.GroupedJob}. This can be null, in which
     *          case the job will be executed with a single job pool.
     */
    public JobGroupPath getJobGroupPath()
    {
        return jobGroupPath;
    }

    /**
     * The state of the rendering limits when this job was requested, i.e. the recursion depths that were already
     * reached, so that executing content asynchronously cannot be used to get fresh limits.
     * <p>
     * Note that this is deliberately not propagated through the {@link org.xwiki.context.ContextStore} like the rest
     * of the context: every context store entry ends up in the job id, and thus in the cache key and in the URL used
     * to fetch the result, so a value that changes with the recursion depth would fragment the asynchronous renderer
     * cache.
     * <p>
     * It is cleared once the job has taken it over so that a cached job status doesn't keep it alive.
     *
     * @return the limits to continue in the job's execution context, may be {@code null}
     * @since 18.7.0RC1
     */
    public RenderingLimitsSnapshot getRenderingLimitsSnapshot()
    {
        return this.limitsSnapshot;
    }

    /**
     * @param limitsSnapshot the limits to continue in the job's execution context
     * @since 18.7.0RC1
     */
    public void setRenderingLimitsSnapshot(RenderingLimitsSnapshot limitsSnapshot)
    {
        this.limitsSnapshot = limitsSnapshot;
    }
}
