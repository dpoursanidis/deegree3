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

import java.util.LinkedHashSet;
import java.util.Set;

import org.deegree.rendering.r3d.opengl.rendering.dem.TextureTile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the fetching (and caching) of {@link TextureTile} instances from {@link TextureTileProvider}s.
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author: schneider $
 * 
 * @version $Revision: $, $Date: $
 */
public class TextureTileManager {

    private static final Logger LOG = LoggerFactory.getLogger( TextureTileManager.class );

    private final Set<TextureTile> cachedTiles = new LinkedHashSet<TextureTile>();

    private final TextureTileProvider[] providers;

    private final int maxCached;

    public TextureTileManager( TextureTileProvider[] providers, int maxCached ) {
        this.providers = providers;
        this.maxCached = maxCached;
    }

    public TextureTile getMachingTile( TextureTileRequest request ) {

        TextureTile tile = null;
        for ( TextureTile candidate : cachedTiles ) {
            if ( request.isFullfilled( candidate ) ) {
                tile = candidate;
                cachedTiles.remove( candidate );
                cachedTiles.add( candidate );
                break;
            }
        }

        if ( tile == null ) {
            tile = getMatchingProvider( request.getMetersPerPixel() ).getTextureTile( request.getMinX(),
                                                                                      request.getMinY(),
                                                                                      request.getMaxX(),
                                                                                      request.getMaxY() );
            addToCache( tile );

        }
        return tile;
    }

    private void addToCache( TextureTile tile ) {
        if ( cachedTiles.size() == maxCached ) {
            TextureTile cacheDrop = cachedTiles.iterator().next();
            cachedTiles.remove( cacheDrop );
        }
        cachedTiles.add( tile );
    }

    private TextureTileProvider getMatchingProvider( double unitsPerPixel ) {
        TextureTileProvider provider = providers[0];
        for ( int i = 0; i < providers.length; i++ ) {
            if ( providers[i].getNativeResolution() > unitsPerPixel ) {
                break;
            }
            provider = providers[i];
        }
        return provider;
    }

    public double getMatchingResolution( double unitsPerPixel ) {
        return getMatchingProvider( unitsPerPixel ).getNativeResolution();
    }

    @Override
    public String toString() {
        return "cached: " + cachedTiles.size();
    }
}
