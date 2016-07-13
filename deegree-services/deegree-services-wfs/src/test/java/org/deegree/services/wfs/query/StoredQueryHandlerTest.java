//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2014 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.services.wfs.query;

import static org.deegree.protocol.wfs.WFSConstants.VERSION_200;
import static org.deegree.protocol.wfs.WFSConstants.WFS_200_NS;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.xmlmatchers.XmlMatchers.hasXPath;
import static org.xmlmatchers.transform.XmlConverters.xml;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.deegree.commons.xml.NamespaceBindings;
import org.deegree.feature.types.FeatureType;
import org.deegree.protocol.wfs.storedquery.CreateStoredQuery;
import org.deegree.protocol.wfs.storedquery.StoredQueryDefinition;
import org.deegree.services.controller.utils.HttpResponseBuffer;
import org.deegree.services.wfs.WebFeatureService;
import org.deegree.services.wfs.WfsFeatureStoreManager;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="mailto:goltz@lat-lon.de">Lyn Goltz</a>
 */
public class StoredQueryHandlerTest {

    private static final NamespaceBindings NS_CONTEXT = new NamespaceBindings();

    private static File managedStoredQueries;

    @BeforeClass
    public static void initNamespaceContext()
                            throws IOException {
        NS_CONTEXT.addNamespace( "wfs", WFS_200_NS );
        managedStoredQueries = Files.createTempDirectory( "managedStoredQueries" ).toFile();
    }

    @Test
    public void testCollectAndSortFeatureTypesToExport_AllFeatureTypes() {
        List<FeatureType> featureTypes = featureTypes();
        StoredQueryHandler storedQueryHandler = new StoredQueryHandler( mockWFS( featureTypes ), new ArrayList<URL>(),
                                                                        managedStoredQueries );

        List<QName> configuredFeatureTypeNames = Collections.emptyList();
        List<QName> featureTypeNamesToExport = storedQueryHandler.collectAndSortFeatureTypesToExport( configuredFeatureTypeNames );

        assertThat( featureTypeNamesToExport.size(), is( featureTypes.size() ) );
        for ( FeatureType featureType : featureTypes ) {
            assertThat( featureTypeNamesToExport, hasItems( featureType.getName() ) );
        }
    }

    @Test
    public void testCollectAndSortFeatureTypesToExport_EmptyFeatureTypeList() {
        List<FeatureType> featureTypes = Collections.emptyList();
        StoredQueryHandler storedQueryHandler = new StoredQueryHandler( mockWFS( featureTypes ), new ArrayList<URL>(),
                                                                        managedStoredQueries );

        List<QName> configuredFeatureTypeNames = Collections.emptyList();
        List<QName> featureTypeNamesToExport = storedQueryHandler.collectAndSortFeatureTypesToExport( configuredFeatureTypeNames );

        assertThat( featureTypeNamesToExport.size(), is( 0 ) );
    }

    @Test
    public void testCollectAndSortFeatureTypesToExport_LimitedConfiguredFeatureTypes() {
        List<FeatureType> featureTypes = featureTypes();
        StoredQueryHandler storedQueryHandler = new StoredQueryHandler( mockWFS( featureTypes ), new ArrayList<URL>(),
                                                                        managedStoredQueries );

        List<QName> configuredFeatureTypeNames = configuredFeatureTypeNames();
        List<QName> featureTypeNamesToExport = storedQueryHandler.collectAndSortFeatureTypesToExport( configuredFeatureTypeNames );

        assertThat( featureTypeNamesToExport.size(), is( 1 ) );

        QName featureTypeNameToExport = featureTypeNamesToExport.get( 0 );
        assertThat( featureTypeNameToExport.getLocalPart(), is( "one" ) );
        assertThat( featureTypeNameToExport.getNamespaceURI(), is( "" ) );
        assertThat( featureTypeNameToExport.getPrefix(), is( "" ) );
    }

    @Test
    public void testDoCreateStoredQuery()
                            throws Exception {
        List<FeatureType> featureTypes = featureTypes();
        StoredQueryHandler storedQueryHandler = new StoredQueryHandler( mockWFS( featureTypes ), new ArrayList<URL>(),
                                                                        managedStoredQueries );

        String id = "mangedStoredQuery";
        CreateStoredQuery request = createStoredQuery( id );
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newInstance().createXMLStreamWriter( outStream );
        storedQueryHandler.doCreateStoredQuery( request, mockHttpResponseBuffer( xmlStreamWriter ) );
        xmlStreamWriter.close();

        assertThat( storedQueryHandler.hasStoredQuery( id ), is( true ) );
        assertThat( xml( outStream.toString() ),
                    hasXPath( "/wfs:CreateStoredQueryResponse[@status='OK']", NS_CONTEXT ) );
    }

    private CreateStoredQuery createStoredQuery( String id ) {
        List<StoredQueryDefinition> queryDefinitions = new ArrayList<StoredQueryDefinition>();
        StoredQueryDefinition queryDefinition = mock( StoredQueryDefinition.class );
        when( queryDefinition.getId() ).thenReturn( id );
        queryDefinitions.add( queryDefinition );
        return new CreateStoredQuery( VERSION_200, "handle", queryDefinitions );
    }

    private List<FeatureType> featureTypes() {
        List<FeatureType> featureTypes = new ArrayList<FeatureType>();
        featureTypes.add( mockFeatureType( "one" ) );
        featureTypes.add( mockFeatureType( "two" ) );
        return featureTypes;
    }

    private List<QName> configuredFeatureTypeNames() {
        List<QName> configuredFeatureTypes = new ArrayList<QName>();
        configuredFeatureTypes.add( new QName( "one" ) );
        return configuredFeatureTypes;
    }

    private FeatureType mockFeatureType( String name ) {
        FeatureType mockedFeatureType = mock( FeatureType.class );
        QName qName = new QName( name );
        when( mockedFeatureType.getName() ).thenReturn( qName );
        return mockedFeatureType;
    }

    private WebFeatureService mockWFS( Collection<FeatureType> featureTypes ) {
        WebFeatureService mockedWfs = mock( WebFeatureService.class );
        WfsFeatureStoreManager mockedStoreManager = mockStoreManager( featureTypes );
        when( mockedWfs.getStoreManager() ).thenReturn( mockedStoreManager );
        return mockedWfs;
    }

    private WfsFeatureStoreManager mockStoreManager( Collection<FeatureType> featureTypes ) {
        WfsFeatureStoreManager mockedStoreManager = mock( WfsFeatureStoreManager.class );
        when( mockedStoreManager.getFeatureTypes() ).thenReturn( featureTypes );
        return mockedStoreManager;
    }

    private HttpResponseBuffer mockHttpResponseBuffer( XMLStreamWriter xmlStreamWriter )
                            throws Exception {
        HttpResponseBuffer mockedResponse = mock( HttpResponseBuffer.class );
        when( mockedResponse.getXMLWriter( anyBoolean() ) ).thenReturn( xmlStreamWriter );
        return mockedResponse;
    }

}