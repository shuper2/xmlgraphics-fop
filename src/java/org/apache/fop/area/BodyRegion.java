/*
 * Copyright 1999-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id: BodyRegion.java,v 1.4 2004/02/27 17:41:26 jeremias Exp $ */

package org.apache.fop.area;

import org.apache.fop.fo.Constants;

/**
 * This class is a container for the areas that may be generated by
 * an fo:region-body.  It extends the RegionReference that is used
 * directly by the other region classes.
 * See fo:region-body definition in the XSL Rec for more information.
 */
public class BodyRegion extends RegionReference {
    private BeforeFloat beforeFloat;  // optional
    private MainReference mainReference; // mandatory
    private Footnote footnote; // optional
    private int columnGap;
    private int columnCount;

    /**
     * Constructor which can read traits directly
     * from an fo:region-body formatting object.
     */
    public BodyRegion(int columnCount, int columnGap, RegionViewport parent) {
        super(Constants.FO_REGION_BODY, parent);
        this.columnCount = columnCount;
        this.columnGap = columnGap;
        mainReference = new MainReference(this);
    }

    /**
     * Set the number of columns for blocks when not spanning
     *
     * @param colCount the number of columns
     */
    public void setColumnCount(int colCount) {
        this.columnCount = colCount;
    }

    /**
     * Get the number of columns when not spanning
     *
     * @return the number of columns
     */
    public int getColumnCount() {
        return this.columnCount;
    }

    /**
     * Set the column gap between columns
     * The length is in millipoints.
     *
     * @param colGap the column gap in millipoints
     */
    public void setColumnGap(int colGap) {
        this.columnGap = colGap;
    }

    /** @return the column-gap value */
    public int getColumnGap() {
        return this.columnGap;
    }
    
    /**
     * Set the before float area.
     *
     * @param bf the before float area
     */
    public void setBeforeFloat(BeforeFloat bf) {
        beforeFloat = bf;
    }

    /**
     * Set the footnote area.
     *
     * @param foot the footnote area
     */
    public void setFootnote(Footnote foot) {
        footnote = foot;
    }

    /**
     * Get the before float area.
     *
     * @return the before float area
     */
    public BeforeFloat getBeforeFloat() {
        return beforeFloat;
    }

    /**
     * Get the main reference area.
     *
     * @return the main reference area
     */
    public MainReference getMainReference() {
        return mainReference;
    }

    /**
     * indicates whether the main reference area has any child areas added to it
     *
     * @return whether the main reference area has any child areas added to it
     */
    public boolean isEmpty() {
        return mainReference == null || mainReference.isEmpty();
    }


    /**
     * Get the footnote area.
     *
     * @return the footnote area
     */
    public Footnote getFootnote() {
        return footnote;
    }

    /**
     * Clone this object.
     *
     * @return a shallow copy of this object
     */
    public Object clone() {
        BodyRegion br = new BodyRegion(columnCount, columnGap, regionViewport);
        br.setCTM(getCTM());
        br.setIPD(getIPD());
        br.beforeFloat = beforeFloat;
        br.mainReference = mainReference;
        br.footnote = footnote;
        return br;
    }
}
