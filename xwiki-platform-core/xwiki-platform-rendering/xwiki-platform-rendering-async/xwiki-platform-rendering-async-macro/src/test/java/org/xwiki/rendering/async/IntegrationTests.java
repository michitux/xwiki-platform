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
package org.xwiki.rendering.async;

import java.util.List;
import java.util.OptionalInt;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.environment.Environment;
import org.xwiki.job.Job;
import org.xwiki.job.JobExecutor;
import org.xwiki.job.Request;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.async.internal.AsyncRendererJobRequest;
import org.xwiki.rendering.async.internal.AsyncRendererJobStatus;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.internal.limits.RenderingLimitsConfiguration;
import org.xwiki.rendering.test.integration.Initialized;
import org.xwiki.rendering.test.integration.Scope;
import org.xwiki.rendering.test.integration.junit5.RenderingTest;
import org.xwiki.rendering.util.ErrorBlockGenerator;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Run all tests found in {@code *.test} files located in the classpath. These {@code *.test} files must follow the
 * conventions described in {@link org.xwiki.rendering.test.integration.TestDataParser}.
 *
 * @version $Id$
 */
@AllComponents
@Scope(pattern = "macroasync.*")
public class IntegrationTests extends RenderingTest
{
    /**
     * The transformation recursion limit used in these tests. Kept very low so that {@code macroasync6.test} doesn't
     * need to nest 20 macros to reach it.
     */
    private static final int RECURSION_LIMIT = 3;

    @Initialized
    public void initialize(MockitoComponentManager cm) throws Exception
    {
        cm.registerMockComponent(Environment.class, "default");
        WikiDescriptorManager wikiDescriptorManager = cm.registerMockComponent(WikiDescriptorManager.class, "default");
        ObservationManager observationManager = cm.registerMockComponent(ObservationManager.class, "default");
        AsyncContext asyncContext = cm.registerMockComponent(AsyncContext.class, "default");
        JobExecutor jobExecutor = cm.registerMockComponent(JobExecutor.class, "default");
        AuthorizationManager authorization = cm.registerMockComponent(AuthorizationManager.class, "default");
        Job job = mock(Job.class);
        AsyncRendererJobRequest jobRequest = new AsyncRendererJobRequest();
        AsyncRendererJobStatus jobStatus = new AsyncRendererJobStatus(jobRequest, observationManager, null);

        // Lower the transformation recursion limit, see RECURSION_LIMIT.
        RenderingLimitsConfiguration limitsConfiguration =
            cm.registerMockComponent(RenderingLimitsConfiguration.class);
        when(limitsConfiguration.getConfiguredRecursionLimit(argThat(type -> "transformation".equals(type.getName()))))
            .thenReturn(OptionalInt.of(RECURSION_LIMIT));

        // Generate a compact error block containing only the root cause message. The real error block generator would
        // include the full stack trace, and the one from xwiki-platform-rendering-xwiki additionally logs an error as
        // there is no TemplateManager in this module.
        ErrorBlockGenerator errorBlockGenerator = cm.registerMockComponent(ErrorBlockGenerator.class);
        when(errorBlockGenerator.generateErrorBlocks(anyBoolean(), any(), any(), any(), any()))
            .thenAnswer(invocation -> {
                Object[] arguments = invocation.getArguments();
                Throwable throwable = (Throwable) arguments[arguments.length - 1];

                return List.of(new WordBlock("error:" + ExceptionUtils.getRootCauseMessage(throwable)));
            });

        when(wikiDescriptorManager.getCurrentWikiId()).thenReturn("wiki");
        when(asyncContext.isEnabled()).thenReturn(true);
        when(job.getStatus()).thenReturn(jobStatus);
        when(jobExecutor.execute(eq("asyncrenderer"), any(Request.class))).thenReturn(job);
        when(authorization.hasAccess(any(Right.class), any(DocumentReference.class), any(EntityReference.class)))
            .thenReturn(true);
    }
}
