//$HeadURL: svn+ssh://mschneider@svn.wald.intevation.org/deegree/base/trunk/resources/eclipse/files_template.xml $
/*----------------    FILE HEADER  ------------------------------------------
 This file is part of deegree.
 Copyright (C) 2001-2009 by:
 Department of Geography, University of Bonn
 http://www.giub.uni-bonn.de/deegree/
 lat/lon GmbH
 http://www.lat-lon.de

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.
 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 Lesser General Public License for more details.
 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 Contact:

 Andreas Poth
 lat/lon GmbH
 Aennchenstr. 19
 53177 Bonn
 Germany
 E-Mail: poth@lat-lon.de

 Prof. Dr. Klaus Greve
 Department of Geography
 University of Bonn
 Meckenheimer Allee 166
 53115 Bonn
 Germany
 E-Mail: greve@giub.uni-bonn.de
 ---------------------------------------------------------------------------*/

package org.deegree.rendering.r3d.opengl.rendering.dem;

import org.deegree.rendering.r3d.opengl.rendering.dem.FragmentTexture;
import org.deegree.rendering.r3d.opengl.rendering.dem.RenderMeshFragment;
import org.deegree.rendering.r3d.opengl.rendering.dem.TextureTile;

/**
 * Represents the request for a {@link FragmentTexture} for a {@link RenderMeshFragment}.
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author: schneider $
 * 
 * @version $Revision: $, $Date: $
 */
public class TextureRequest {

    private float MIN_METERS_PER_PIXEL = 0.09f;

    float minX;

    float minY;

    float maxX;

    float maxY;

    float metersPerPixel;

    private RenderMeshFragment fragment;

    public TextureRequest( RenderMeshFragment fragment, float minX, float minY, float maxX, float maxY,
                           float metersPerPixel ) {
        this.fragment = fragment;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        if ( metersPerPixel < MIN_METERS_PER_PIXEL ) {
            metersPerPixel = MIN_METERS_PER_PIXEL;
        }
        this.metersPerPixel = metersPerPixel;
    }

    public RenderMeshFragment getFragment () {
        return fragment;
    }
    
    /**
     * Returns whether this {@link TextureRequest} supersedes another {@link TextureRequest}.
     * <p>
     * This is true iff:
     * <ul>
     * <li>the bbox of the other request lies completely inside the bbox of this request (or coincides with it)</li>
     * <li>the meters per pixel of the other request is less than or equal to the meters per pixel of this request</li>
     * </ul>
     * </p>
     * 
     * @param that
     * @return true, if this request supersedes the given request, false otherwise
     */
    public boolean supersedes( TextureRequest that ) {
        return this.minX <= that.minX && this.minY <= that.minY && this.maxX >= that.maxX && this.maxY >= that.maxY
               && this.metersPerPixel == that.metersPerPixel;
    }

    public void merge( TextureRequest otherRequest ) {
        if ( this.metersPerPixel > otherRequest.metersPerPixel ) {
            this.metersPerPixel = otherRequest.metersPerPixel;
        }
        if ( this.minX > otherRequest.minX ) {
            this.minX = otherRequest.minX;
        }
        if ( this.maxX < otherRequest.maxX ) {
            this.maxX = otherRequest.maxX;
        }
        if ( this.minY > otherRequest.minY ) {
            this.minY = otherRequest.minY;
        }
        if ( this.maxY < otherRequest.maxY ) {
            this.maxY = otherRequest.maxY;
        }
    }

    public float getMinX() {
        return minX;
    }

    public float getMinY() {
        return minY;
    }

    public float getMaxX() {
        return maxX;
    }

    public float getMaxY() {
        return maxY;
    }

    public float getMetersPerPixel() {
        return metersPerPixel;
    }

    @Override
    public int hashCode() {
        return fragment.getId();
    }

    public boolean shareCorner( TextureRequest that ) {
        return this.shareCornerNE( that ) || this.shareCornerNW( that ) || this.shareCornerSE( that )
               || this.shareCornerSW( that );
    }

    public boolean shareCornerNW( TextureRequest that ) {
        return this.minX == that.minX && this.maxY == that.maxY;
    }

    public boolean shareCornerNE( TextureRequest that ) {
        return this.maxX == that.maxX && this.maxY == that.maxY;
    }

    public boolean shareCornerSW( TextureRequest that ) {
        return this.minX == that.minX && this.minY == that.minY;
    }

    public boolean shareCornerSE( TextureRequest that ) {
        return this.maxX == that.maxX && this.minY == that.minY;
    }

    public boolean equals( Object o ) {
        if ( !( o instanceof TextureRequest ) ) {
            return false;
        }
        TextureRequest that = (TextureRequest) o;
        return this.fragment.getId() == that.fragment.getId() && ( this.metersPerPixel - that.metersPerPixel ) < 0.001f;
    }

    @Override
    public String toString() {
        return "(" + minX + "," + minY + "," + maxX + "," + maxY + "), meter/pixel: " + metersPerPixel;
    }

    public boolean isFullfilled( TextureTile candidate ) {
        return this.minX >= candidate.getMinX() && this.minY >= candidate.getMinY() && this.maxX <= candidate.getMaxX()
               && this.maxY <= candidate.getMaxY() && this.metersPerPixel >= candidate.getMetersPerPixel();
    }
}
