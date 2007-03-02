/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.render.ps;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.image.FopImage;
import org.apache.fop.image.ImageFactory;
import org.apache.xmlgraphics.ps.DSCConstants;
import org.apache.xmlgraphics.ps.PSGenerator;
import org.apache.xmlgraphics.ps.dsc.DSCException;
import org.apache.xmlgraphics.ps.dsc.DSCFilter;
import org.apache.xmlgraphics.ps.dsc.DSCParser;
import org.apache.xmlgraphics.ps.dsc.DSCParserConstants;
import org.apache.xmlgraphics.ps.dsc.DefaultNestedDocumentHandler;
import org.apache.xmlgraphics.ps.dsc.ResourceTracker;
import org.apache.xmlgraphics.ps.dsc.events.DSCComment;
import org.apache.xmlgraphics.ps.dsc.events.DSCCommentDocumentNeededResources;
import org.apache.xmlgraphics.ps.dsc.events.DSCCommentDocumentSuppliedResources;
import org.apache.xmlgraphics.ps.dsc.events.DSCCommentLanguageLevel;
import org.apache.xmlgraphics.ps.dsc.events.DSCCommentPage;
import org.apache.xmlgraphics.ps.dsc.events.DSCCommentPages;
import org.apache.xmlgraphics.ps.dsc.events.DSCEvent;
import org.apache.xmlgraphics.ps.dsc.events.DSCHeaderComment;
import org.apache.xmlgraphics.ps.dsc.events.PostScriptComment;
import org.apache.xmlgraphics.ps.dsc.tools.DSCTools;

/**
 * This class is used when two-pass production is used to generate the PostScript file (setting
 * "optimize-resources"). It uses the DSC parser from XML Graphics Commons to go over the
 * temporary file generated by the PSRenderer and adds all used fonts and images as resources
 * to the PostScript file.
 */
public class ResourceHandler implements DSCParserConstants {

    /**
     * Rewrites the temporary PostScript file generated by PSRenderer adding all needed resources
     * (fonts and images).
     * @param userAgent the FO user agent
     * @param in the InputStream for the temporary PostScript file
     * @param out the OutputStream to write the finished file to
     * @param fontInfo the font information
     * @param resTracker the resource tracker to use
     * @param formResources Contains all forms used by this document (maintained by PSRenderer)
     * @param pageCount the number of pages (given here because PSRenderer writes an "(atend)")
     * @throws DSCException If there's an error in the DSC structure of the PS file
     * @throws IOException In case of an I/O error
     */
    public static void process(FOUserAgent userAgent, InputStream in, OutputStream out, 
            FontInfo fontInfo, ResourceTracker resTracker, Map formResources, int pageCount)
                    throws DSCException, IOException {
        DSCParser parser = new DSCParser(in);
        PSGenerator gen = new PSGenerator(out);
        parser.setNestedDocumentHandler(new DefaultNestedDocumentHandler(gen));
        
        //Skip DSC header
        DSCHeaderComment header = DSCTools.checkAndSkipDSC30Header(parser);
        header.generate(gen);
        
        parser.setFilter(new DSCFilter() {
            private final Set filtered = new java.util.HashSet();
            {
                //We rewrite those as part of the processing
                filtered.add(DSCConstants.PAGES);
                filtered.add(DSCConstants.DOCUMENT_NEEDED_RESOURCES);
                filtered.add(DSCConstants.DOCUMENT_SUPPLIED_RESOURCES);
            }
            public boolean accept(DSCEvent event) {
                if (event.isDSCComment()) {
                    //Filter %%Pages which we add manually from a parameter
                    return !(filtered.contains(event.asDSCComment().getName()));
                } else {
                    return true;
                }
            }
        });

        //Get PostScript language level (may be missing)
        while (true) {
            DSCEvent event = parser.nextEvent();
            if (event == null) {
                reportInvalidDSC();
            }
            if (DSCTools.headerCommentsEndHere(event)) {
                //Set number of pages
                DSCCommentPages pages = new DSCCommentPages(pageCount);
                pages.generate(gen);

                PSFontUtils.determineSuppliedFonts(resTracker, fontInfo, fontInfo.getUsedFonts());
                registerSuppliedForms(resTracker, formResources);
                
                //Supplied Resources
                DSCCommentDocumentSuppliedResources supplied 
                    = new DSCCommentDocumentSuppliedResources(
                            resTracker.getDocumentSuppliedResources());
                supplied.generate(gen);
                
                //Needed Resources
                DSCCommentDocumentNeededResources needed 
                    = new DSCCommentDocumentNeededResources(
                            resTracker.getDocumentNeededResources());
                needed.generate(gen);

                //Write original comment that ends the header comments
                event.generate(gen);
                break;
            }
            if (event.isDSCComment()) {
                DSCComment comment = event.asDSCComment();
                if (DSCConstants.LANGUAGE_LEVEL.equals(comment.getName())) {
                    DSCCommentLanguageLevel level = (DSCCommentLanguageLevel)comment;
                    gen.setPSLevel(level.getLanguageLevel());
                }
            }
            event.generate(gen);
        }
        
        //Skip to the FOPFontSetup
        PostScriptComment fontSetupPlaceholder = parser.nextPSComment("FOPFontSetup", gen);
        if (fontSetupPlaceholder == null) {
            throw new DSCException("Didn't find %FOPFontSetup comment in stream");
        }
        PSFontUtils.writeFontDict(gen, fontInfo, fontInfo.getUsedFonts());
        generateForms(resTracker, userAgent, formResources, gen);

        //Skip the prolog and to the first page
        DSCComment pageOrTrailer = parser.nextDSCComment(DSCConstants.PAGE, gen);
        if (pageOrTrailer == null) {
            throw new DSCException("Page expected, but none found");
        }
        
        //Process individual pages (and skip as necessary)
        while (true) {
            DSCCommentPage page = (DSCCommentPage)pageOrTrailer;
            page.generate(gen);
            pageOrTrailer = DSCTools.nextPageOrTrailer(parser, gen);
            if (pageOrTrailer == null) {
                reportInvalidDSC();
            } else if (!DSCConstants.PAGE.equals(pageOrTrailer.getName())) {
                pageOrTrailer.generate(gen);
                break;
            }
        }
        
        //Write the rest
        while (parser.hasNext()) {
            DSCEvent event = parser.nextEvent();
            event.generate(gen);
        }
    }

    private static void reportInvalidDSC() throws DSCException {
        throw new DSCException("File is not DSC-compliant: Unexpected end of file");
    }

    private static void registerSuppliedForms(ResourceTracker resTracker, Map formResources)
            throws IOException {
        Iterator iter = formResources.values().iterator();
        while (iter.hasNext()) {
            PSImageFormResource form = (PSImageFormResource)iter.next();
            resTracker.registerSuppliedResource(form);
        }
    }

    private static void generateForms(ResourceTracker resTracker, FOUserAgent userAgent, 
            Map formResources, PSGenerator gen) throws IOException {
        Iterator iter = formResources.values().iterator();
        while (iter.hasNext()) {
            PSImageFormResource form = (PSImageFormResource)iter.next();
            ImageFactory fact = userAgent.getFactory().getImageFactory();
            FopImage image = fact.getImage(form.getImageURI(), userAgent);
            if (image == null) {
                throw new NullPointerException("Image not found: " + form.getImageURI());
            }
            PSImageUtils.generateFormResourceForImage(image, form, gen);
        }
    }

}
