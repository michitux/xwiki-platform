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
package org.xwiki.flamingo.test.docker;

import java.net.URL;
import java.util.Base64;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.ComparePage;
import org.xwiki.test.ui.po.HistoryPane;
import org.xwiki.test.ui.po.ViewPage;
import org.xwiki.test.ui.po.diff.RenderedChanges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to the compare versions feature.
 *
 * @version $Id$
 */
@UITest(properties = {
    // Trust picsum.photos to allow the rendered diff to download images from it
    "xwikiPropertiesAdditionalProperties=url.trustedDomains=picsum.photos"
})
class CompareIT
{
    @Test
    @Order(1)
    void compareRenderedImageChanges(TestUtils setup, TestReference testReference) throws Exception
    {
        setup.loginAsSuperAdmin();
        String imageSyntax = "[[image:%s]]";
        // Use picsum.photos as image source as the XWiki installation in Docker cannot access itself to get the
        // image as attachment.
        String firstImage = "https://picsum.photos/seed/test/90/90?v=1";
        ViewPage viewPage = setup.createPage(testReference, String.format(imageSyntax, firstImage));
        String firstRevision = viewPage.getMetaDataValue("version");
        // Create a second revision with a different URL but same image content.
        String secondImage = "https://picsum.photos/seed/test/90/90?v=2";
        viewPage = setup.createPage(testReference, String.format(imageSyntax, secondImage));
        String secondRevision = viewPage.getMetaDataValue("version");

        // Open the history pane.
        HistoryPane historyPane = viewPage.openHistoryDocExtraPane();
        ComparePage compare = historyPane.compare(firstRevision, secondRevision);
        RenderedChanges renderedChanges = compare.getChangesPane().getRenderedChanges();
        assertTrue(renderedChanges.hasNoChanges());

        // Create a third revision with a different image.
        String thirdImage = "https://picsum.photos/seed/test2/90/90";
        viewPage = setup.createPage(testReference, String.format(imageSyntax, thirdImage));
        String thirdRevision = viewPage.getMetaDataValue("version");

        // Open the history pane.
        historyPane = viewPage.openHistoryDocExtraPane();
        compare = historyPane.compare(secondRevision, thirdRevision);
        renderedChanges = compare.getChangesPane().getRenderedChanges();
        assertFalse(renderedChanges.hasNoChanges());
        List<WebElement> changes = renderedChanges.getChangedBlocks();
        assertEquals(2, changes.size());

        // Check that the first change is the deletion and the second change the insertion of the new image.
        WebElement firstChange = changes.get(0);
        WebElement secondChange = changes.get(1);
        assertEquals("deleted", firstChange.getAttribute("data-xwiki-html-diff-block"));
        assertEquals("inserted", secondChange.getAttribute("data-xwiki-html-diff-block"));
        WebElement deletedImage = firstChange.findElement(By.tagName("img"));
        WebElement insertedImage = secondChange.findElement(By.tagName("img"));

        // Check that the src attribute of the deleted image is the original URL.
        assertEquals(secondImage, deletedImage.getAttribute("src"));

        // Compute the expected base64-encoded content of the third image. The HTML diff embeds both images but
        // replaces the deleted image by the original URL again after the diff computation.
        String expectedInsertedImageContent = Base64.getEncoder().encodeToString(
            IOUtils.toByteArray(new URL(thirdImage).openStream()));
        assertEquals("data:image/jpeg;base64," + expectedInsertedImageContent, insertedImage.getAttribute("src"));
    }
}
