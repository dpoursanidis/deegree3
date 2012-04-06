//$HeadURL: svn+ssh://mschneider@svn.wald.intevation.org/deegree/deegree3/commons/trunk/src/org/deegree/model/feature/Feature.java $
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
 Department of Geography, University of Bonn
 and
 lat/lon GmbH

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

package org.deegree.gml.feature;

import static javax.xml.XMLConstants.NULL_NS_URI;
import static org.deegree.commons.xml.CommonNamespaces.GML_PREFIX;
import static org.deegree.commons.xml.CommonNamespaces.XLNNS;
import static org.deegree.commons.xml.CommonNamespaces.XSINS;
import static org.deegree.commons.xml.CommonNamespaces.XSI_PREFIX;
import static org.deegree.feature.types.property.ValueRepresentation.REMOTE;
import static org.deegree.gml.GMLVersion.GML_2;
import static org.deegree.protocol.wfs.WFSConstants.WFS_NS;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.deegree.commons.tom.ElementNode;
import org.deegree.commons.tom.TypedObjectNode;
import org.deegree.commons.tom.array.TypedObjectNodeArray;
import org.deegree.commons.tom.gml.GMLObject;
import org.deegree.commons.tom.gml.GMLReference;
import org.deegree.commons.tom.gml.property.Property;
import org.deegree.commons.tom.gml.property.PropertyType;
import org.deegree.commons.tom.ows.CodeType;
import org.deegree.commons.tom.ows.StringOrRef;
import org.deegree.commons.tom.primitive.BaseType;
import org.deegree.commons.tom.primitive.PrimitiveValue;
import org.deegree.commons.uom.Length;
import org.deegree.commons.uom.Measure;
import org.deegree.commons.xml.stax.StAXExportingHelper;
import org.deegree.cs.exceptions.TransformationException;
import org.deegree.cs.exceptions.UnknownCRSException;
import org.deegree.feature.Feature;
import org.deegree.feature.FeatureCollection;
import org.deegree.feature.GenericFeatureCollection;
import org.deegree.feature.property.ExtraProps;
import org.deegree.feature.property.GenericProperty;
import org.deegree.feature.types.property.ArrayPropertyType;
import org.deegree.feature.types.property.CodePropertyType;
import org.deegree.feature.types.property.CustomPropertyType;
import org.deegree.feature.types.property.EnvelopePropertyType;
import org.deegree.feature.types.property.FeaturePropertyType;
import org.deegree.feature.types.property.GeometryPropertyType;
import org.deegree.feature.types.property.LengthPropertyType;
import org.deegree.feature.types.property.MeasurePropertyType;
import org.deegree.feature.types.property.SimplePropertyType;
import org.deegree.feature.types.property.StringOrRefPropertyType;
import org.deegree.geometry.Envelope;
import org.deegree.geometry.Geometry;
import org.deegree.gml.GMLStreamWriter;
import org.deegree.gml.commons.AbstractGMLObjectWriter;
import org.deegree.protocol.wfs.query.ProjectionClause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stream-based GML writer for {@link Feature} and {@link FeatureCollection} instances.
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author <a href="mailto:ionita@lat-lon.de">Andrei Ionita</a>
 * @author last edited by: $Author:$
 * 
 * @version $Revision:$, $Date:$
 */
public class GMLFeatureWriter extends AbstractGMLObjectWriter {

    private static final Logger LOG = LoggerFactory.getLogger( GMLFeatureWriter.class );

    private final QName fidAttr;

    private final String gmlNull;

    // TODO handle properties that are more complex XPath-expressions
    private final Map<QName, ProjectionClause> projections = new HashMap<QName, ProjectionClause>();

    // export all levels by default
    private final int traverseXlinkDepth;

    private final boolean exportSf;

    private final boolean outputGeometries;

    private final GMLForwardReferenceHandler additionalObjectHandler;

    private final boolean exportExtraProps;

    private final boolean exportBoundedBy;

    private final PropertyType boundedByPt;
    
    private static final QName XSI_NIL = new QName( XSINS, "nil", "xsi" );

    /**
     * Creates a new {@link GMLFeatureWriter} instance.
     * 
     * @param gmlStreamWriter
     *            GML stream writer, must not be <code>null</code>
     */
    public GMLFeatureWriter( GMLStreamWriter gmlStreamWriter ) {
        super( gmlStreamWriter );

        traverseXlinkDepth = gmlStreamWriter.getXlinkDepth();

        if ( gmlStreamWriter.getProjections() != null ) {
            for ( ProjectionClause projection : gmlStreamWriter.getProjections() ) {
                QName qName = projection.getPropertyName().getAsQName();
                if ( qName != null ) {
                    projections.put( qName, projection );
                } else {
                    LOG.warn( "Currently, only simple qualified names are supported for projection." );
                }
            }
        }

        if ( !version.equals( GML_2 ) ) {
            fidAttr = new QName( gmlNs, "id" );
            gmlNull = "Null";
        } else {
            fidAttr = new QName( NULL_NS_URI, "fid" );
            gmlNull = "null";
        }

        this.outputGeometries = gmlStreamWriter.getOutputGeometries();
        this.exportSf = false;
        this.additionalObjectHandler = gmlStreamWriter.getAdditionalObjectHandler();
        this.exportExtraProps = gmlStreamWriter.getExportExtraProps();
        this.boundedByPt = new EnvelopePropertyType( new QName( gmlNs, "boundedBy" ), 0, 1, null, null );
        this.exportBoundedBy = gmlStreamWriter.getGenerateBoundedByForFeatures();
    }

    /**
     * Exports the given {@link Feature} (or {@link FeatureCollection}).
     * 
     * @param feature
     *            feature to be exported, must not be <code>null</code>
     * @throws XMLStreamException
     * @throws UnknownCRSException
     * @throws TransformationException
     */
    public void export( Feature feature )
                            throws XMLStreamException, UnknownCRSException, TransformationException {
        export( feature, 0, traverseXlinkDepth );
    }

    public void export( Feature feature, int level )
                            throws XMLStreamException, UnknownCRSException, TransformationException {
        export( feature, level, traverseXlinkDepth );
    }

    /**
     * TODO merge with other schema location possibilities
     * 
     * @param fc
     * @param noNamespaceSchemaLocation
     *            may be null
     * @param bindings
     *            optional additional schema locations
     * @throws XMLStreamException
     * @throws TransformationException
     * @throws UnknownCRSException
     */
    public void export( FeatureCollection fc, String noNamespaceSchemaLocation, Map<String, String> bindings )
                            throws XMLStreamException, UnknownCRSException, TransformationException {

        LOG.debug( "Exporting generic feature collection." );
        if ( fc.getId() != null ) {
            exportedIds.add( fc.getId() );
        }

        writer.setDefaultNamespace( WFS_NS );
        writer.writeStartElement( WFS_NS, "FeatureCollection" );
        writer.writeDefaultNamespace( WFS_NS );
        writer.writeNamespace( XSI_PREFIX, XSINS );
        writer.writeNamespace( GML_PREFIX, gmlNs );

        if ( fc.getId() != null ) {
            if ( fidAttr.getNamespaceURI() == NULL_NS_URI ) {
                writer.writeAttribute( fidAttr.getLocalPart(), fc.getId() );
            } else {
                writer.writeAttribute( fidAttr.getNamespaceURI(), fidAttr.getLocalPart(), fc.getId() );
            }
        }

        if ( noNamespaceSchemaLocation != null ) {
            writer.writeAttribute( XSINS, "noNamespaceSchemaLocation", noNamespaceSchemaLocation );
        }
        if ( bindings != null && !bindings.isEmpty() ) {

            String locs = null;
            for ( Entry<String, String> e : bindings.entrySet() ) {
                if ( locs == null ) {
                    locs = "";
                } else {
                    locs += " ";
                }
                locs += e.getKey() + " " + e.getValue();
            }
            writer.writeAttribute( XSINS, "schemaLocation", locs );
        }

        exportBoundedBy( fc.getEnvelope(), true );

        for ( Feature f : fc ) {
            writer.writeStartElement( gmlNs, "featureMember" );
            export( f );
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private void exportBoundedBy( Envelope env, boolean indicateMissing )
                            throws XMLStreamException, UnknownCRSException, TransformationException {

        if ( env != null || indicateMissing ) {
            writer.writeStartElement( gmlNs, "boundedBy" );
            if ( env != null ) {
                gmlStreamWriter.getGeometryWriter().exportEnvelope( env );
            } else {
                writer.writeStartElement( gmlNs, gmlNull );
                writer.writeCharacters( "missing" );
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

    private void export( Feature feature, int currentLevel, int maxInlineLevels )
                            throws XMLStreamException, UnknownCRSException, TransformationException {

        if ( feature.getId() != null ) {
            exportedIds.add( feature.getId() );
        }
        if ( feature instanceof GenericFeatureCollection ) {
            LOG.debug( "Exporting generic feature collection." );
            writeStartElementWithNS( gmlNs, "FeatureCollection" );
            if ( feature.getId() != null ) {
                if ( fidAttr.getNamespaceURI() == NULL_NS_URI ) {
                    writer.writeAttribute( fidAttr.getLocalPart(), feature.getId() );
                } else {
                    writeAttributeWithNS( fidAttr.getNamespaceURI(), fidAttr.getLocalPart(), feature.getId() );
                }
            }
            exportBoundedBy( feature.getEnvelope(), false );
            for ( Feature member : ( (FeatureCollection) feature ) ) {
                String memberFid = member.getId();
                writeStartElementWithNS( gmlNs, "featureMember" );
                if ( memberFid != null && exportedIds.contains( memberFid ) ) {
                    writeAttributeWithNS( XLNNS, "href", "#" + memberFid );
                } else {
                    export( member, currentLevel + 1, maxInlineLevels );
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
        } else {
            QName featureName = feature.getName();
            LOG.debug( "Exporting Feature {} with ID {}", featureName, feature.getId() );
            String namespaceURI = featureName.getNamespaceURI();
            String localName = featureName.getLocalPart();
            writeStartElementWithNS( namespaceURI, localName );

            if ( feature.getId() != null ) {
                if ( fidAttr.getNamespaceURI() == NULL_NS_URI ) {
                    writer.writeAttribute( fidAttr.getLocalPart(), feature.getId() );
                } else {
                    writeAttributeWithNS( fidAttr.getNamespaceURI(), fidAttr.getLocalPart(), feature.getId() );
                }
            }

            List<Property> props = feature.getProperties();
            if ( exportBoundedBy ) {
                props = augmentBoundedBy( feature );
            }

            for ( Property prop : props ) {
                if ( currentLevel == 0 ) {
                    maxInlineLevels = getInlineLevels( prop );
                }
                export( prop, currentLevel, maxInlineLevels );
            }

            if ( exportExtraProps ) {
                ExtraProps extraProps = feature.getExtraProperties();
                if ( extraProps != null ) {
                    for ( Property prop : extraProps.getProperties() ) {
                        // really export all levels in any case?
                        export( prop, 0, -1 );
                    }
                }
            }
            writer.writeEndElement();
        }
    }

    private List<Property> augmentBoundedBy( Feature f ) {
        LinkedList<Property> props = new LinkedList<Property>( f.getProperties() );
        for ( int i = 0; i < props.size(); i++ ) {
            QName name = props.get( i ).getName();
            if ( !gmlNs.equals( name.getNamespaceURI() ) || name.getLocalPart().equals( "location" ) ) {
                // not a GML property or gml:location -> gml:boundedBy must be included right before it
                Property boundedBy = getBoundedBy( f );
                if ( boundedBy != null ) {
                    props.add( i, boundedBy );
                }
                break;
            } else if ( name.getLocalPart().equals( "boundedBy" ) ) {
                // already present -> don't include it
                break;
            }
        }
        return props;
    }

    private Property getBoundedBy( Feature f ) {
        Envelope env = f.getEnvelope();
        if ( env == null ) {
            env = f.calcEnvelope();
        }
        if ( env == null ) {
            return null;
        }
        return new GenericProperty( boundedByPt, env );
    }

    private int getInlineLevels( Property prop ) {
        ProjectionClause projection = projections.get( prop.getName() );
        if ( projection != null && projection.getResolveParams() != null
             && projection.getResolveParams().getDepth() != null ) {
            if ( projection.getResolveParams().getDepth().equals( "*" ) ) {
                return -1;
            }
            return Integer.parseInt( projection.getResolveParams().getDepth() );
        }
        return traverseXlinkDepth;
    }

    public void export( Property property, int currentLevel, int maxInlineLevels )
                            throws XMLStreamException, UnknownCRSException, TransformationException {

        QName propName = property.getName();
        PropertyType propertyType = property.getType();
        if ( propertyType.getMinOccurs() == 0 ) {
            LOG.debug( "Optional property '" + propName + "', checking if it is requested." );
            if ( !isPropertyRequested( propName ) ) {
                LOG.debug( "Skipping it." );
                return;
            }
            // required for WMS:
            if ( !outputGeometries && propertyType instanceof GeometryPropertyType ) {
                LOG.debug( "Skipping it since geometries should not be output." );
                return;
            }
        }

        TypedObjectNode value = property.getValue();

        // if ( value instanceof GenericXMLElement ) {
        // writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
        // export( value, currentLevel, maxInlineLevels );
        // writer.writeEndElement();
        // return;
        // }

        // TODO check for GML 2 properties (gml:pointProperty, ...) and export
        // as "app:gml2PointProperty" for GML 3
        boolean nilled = false;
        TypedObjectNode nil = property.getAttributes().get( XSI_NIL );
        if ( nil instanceof PrimitiveValue ) {
            nilled = Boolean.TRUE.equals( ( (PrimitiveValue) nil ).getValue() );
        }
        if ( propertyType instanceof FeaturePropertyType ) {
            if ( nilled ) {
                writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                writeAttributeWithNS( XSINS, "nil", "true" );
                endEmptyElement();
            } else {
                exportFeatureProperty( (FeaturePropertyType) propertyType, (Feature) value, currentLevel,
                                       maxInlineLevels );
            }
        } else if ( propertyType instanceof SimplePropertyType ) {
            if ( nilled ) {
                writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                writeAttributeWithNS( XSINS, "nil", "true" );
                endEmptyElement();
            } else {
                // must be a primitive value
                PrimitiveValue pValue = (PrimitiveValue) value;
                writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                if ( pValue != null ) {
                    // TODO
                    if ( pValue.getType().getBaseType() == BaseType.DECIMAL ) {
                        writer.writeCharacters( pValue.getValue().toString() );
                    } else {
                        writer.writeCharacters( pValue.getAsText() );
                    }
                }
                writer.writeEndElement();
            }
        } else if ( propertyType instanceof GeometryPropertyType ) {
            if ( nilled ) {
                writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                writeAttributeWithNS( XSINS, "nil", "true" );
                endEmptyElement();
            } else {
                Geometry gValue = (Geometry) value;
                if ( !exportSf && gValue.getId() != null && exportedIds.contains( gValue.getId() ) ) {
                    writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                    writeAttributeWithNS( XLNNS, "href", "#" + gValue.getId() );
                    endEmptyElement();
                } else {
                    writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                    if ( gValue.getId() != null ) {
                        // WFS CITE 1.1.0 test requirement (wfs:GetFeature.XLink-POST-XML-10)
                        writer.writeComment( "Inlined geometry '" + gValue.getId() + "'" );
                    }
                    gmlStreamWriter.getGeometryWriter().export( (Geometry) value );
                    writer.writeEndElement();
                }
            }
        } else if ( propertyType instanceof CodePropertyType ) {
            writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
            if ( nilled ) {
                writeAttributeWithNS( XSINS, "nil", "true" );
            }
            CodeType codeType = (CodeType) value;

            if ( codeType != null ) {
                if ( codeType.getCodeSpace() != null && codeType.getCodeSpace().length() > 0 ) {
                    if ( GML_2 != version ) {
                        writer.writeAttribute( "codeSpace", codeType.getCodeSpace() );
                    }
                }
                writer.writeCharacters( codeType.getCode() );
            }
            writer.writeEndElement();
        } else if ( propertyType instanceof EnvelopePropertyType ) {
            if ( nilled ) {
                writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                writeAttributeWithNS( XSINS, "nil", "true" );
                endEmptyElement();
            } else {
                writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                if ( value != null ) {
                    gmlStreamWriter.getGeometryWriter().exportEnvelope( (Envelope) value );
                } else {
                    writeStartElementWithNS( gmlNs, gmlNull );
                    writer.writeCharacters( "missing" );
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
        } else if ( propertyType instanceof LengthPropertyType ) {
            Length length = (Length) value;
            writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
            if ( GML_2 != version ) {
                if ( nilled ) {
                    writeAttributeWithNS( XSINS, "nil", "true" );
                }
                writer.writeAttribute( "uom", length.getUomUri() );
            }
            if ( !nilled ) {
                writer.writeCharacters( String.valueOf( length.getValue() ) );
            }
            writer.writeEndElement();
        } else if ( propertyType instanceof MeasurePropertyType ) {
            Measure measure = (Measure) value;
            writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
            if ( GML_2 != version ) {
                writer.writeAttribute( "uom", measure.getUomUri() );
            }
            writer.writeCharacters( String.valueOf( measure.getValue() ) );
            writer.writeEndElement();
        } else if ( propertyType instanceof StringOrRefPropertyType ) {
            StringOrRef stringOrRef = (StringOrRef) value;
            if ( stringOrRef.getString() == null || stringOrRef.getString().length() == 0 ) {
                writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                if ( stringOrRef.getRef() != null ) {
                    writeAttributeWithNS( XLNNS, "href", stringOrRef.getRef() );
                }
                if ( nilled ) {
                    writeAttributeWithNS( XSINS, "nil", "true" );
                }
                endEmptyElement();
            } else {
                writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                if ( nilled ) {
                    writeAttributeWithNS( XSINS, "nil", "true" );
                }

                if ( stringOrRef.getRef() != null ) {
                    writeAttributeWithNS( XLNNS, "href", stringOrRef.getRef() );
                }
                if ( !nilled && stringOrRef.getString() != null ) {
                    writer.writeCharacters( stringOrRef.getString() );
                }
                writer.writeEndElement();
            }
        } else if ( propertyType instanceof CustomPropertyType ) {
            writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
            if ( property.getAttributes() != null ) {
                for ( Entry<QName, PrimitiveValue> attr : property.getAttributes().entrySet() ) {
                    StAXExportingHelper.writeAttribute( writer, attr.getKey(), attr.getValue().getAsText() );
                }
            }
            if ( property.getChildren() != null ) {
                for ( TypedObjectNode childNode : property.getChildren() ) {
                    export( childNode, currentLevel, maxInlineLevels );
                }
            }
            writer.writeEndElement();
        } else if ( propertyType instanceof ArrayPropertyType ) {
            if ( nilled ) {
                writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                writeAttributeWithNS( XSINS, "nil", "true" );
                endEmptyElement();
            } else {
                writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                export( (TypedObjectNode) property.getValue(), currentLevel, maxInlineLevels );
                writer.writeEndElement();
            }
        } else {
            throw new RuntimeException( "Internal error. Unhandled property type '" + propertyType.getClass() + "'" );
        }
    }

    private void exportFeatureProperty( FeaturePropertyType pt, Feature subFeature, int currentLevel,
                                        int maxInlineLevels )
                            throws XMLStreamException, UnknownCRSException, TransformationException {

        QName propName = pt.getName();
        LOG.debug( "Exporting feature property '" + propName + "'" );
        if ( subFeature == null ) {
            writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
            endEmptyElement();
        } else if ( !( subFeature instanceof FeatureReference ) || ( (FeatureReference) subFeature ).isLocal() ) {
            // normal feature or local feature reference
            String subFid = subFeature.getId();
            if ( subFid == null ) {
                // no feature id -> no other chance, but inlining it
                writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                writer.writeComment( "Inlined feature '" + subFid + "'" );
                export( subFeature, currentLevel + 1, maxInlineLevels );
                writer.writeEndElement();
            } else {
                // has feature id
                if ( exportedIds.contains( subFid ) ) {
                    // already exported -> put a local xlink to the feature instance
                    writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                    writeAttributeWithNS( XLNNS, "href", "#" + subFid );
                    endEmptyElement();
                } else {
                    // not exported yet
                    if ( maxInlineLevels == -1 || ( maxInlineLevels > 0 && currentLevel < maxInlineLevels ) ) {
                        // force export (maximum number of inline levels not reached)
                        if ( pt.getAllowedRepresentation() == REMOTE ) {
                            // only export by reference possible
                            writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                            if ( additionalObjectHandler != null ) {
                                String uri = additionalObjectHandler.requireObject( (GMLReference<?>) subFeature );
                                writeAttributeWithNS( XLNNS, "href", uri );
                            } else {
                                LOG.debug( "No additionalObjectHandler registered. Exporting xlink-only feature property inline." );
                                String uri = remoteXlinkTemplate.replace( "{}", subFid );
                                writeAttributeWithNS( XLNNS, "href", uri );
                            }
                            endEmptyElement();
                        } else {
                            // export inline
                            exportedIds.add( subFeature.getId() );
                            writeStartElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                            writer.writeComment( "Inlined feature '" + subFid + "'" );
                            export( subFeature, currentLevel + 1, maxInlineLevels );
                            writer.writeEndElement();
                        }
                    } else {
                        // don't force export (maximum number of inline levels reached)
                        if ( !( subFeature instanceof GMLReference<?> ) ) {
                            LOG.warn( "References not expected at this point. Needs investigation." );
                        }
                        writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                        if ( additionalObjectHandler != null && subFeature instanceof GMLReference<?> ) {
                            String uri = additionalObjectHandler.handleReference( (GMLReference<?>) subFeature );
                            writeAttributeWithNS( XLNNS, "href", uri );
                        } else {
                            LOG.warn( "No additionalObjectHandler registered. Exporting xlink-only feature inline." );
                            String uri = remoteXlinkTemplate.replace( "{}", subFid );
                            writeAttributeWithNS( XLNNS, "href", uri );
                        }
                        endEmptyElement();
                    }
                }
            }
        } else {
            // remote feature reference
            FeatureReference ref = (FeatureReference) subFeature;
            if ( ( maxInlineLevels > 0 && currentLevel < maxInlineLevels ) || remoteXlinkTemplate == null
                 || maxInlineLevels == -1 ) {
                String uri = ref.getURI();
                try {
                    new URL( uri );
                    throw new UnsupportedOperationException(
                                                             "Inlining of remote feature references is not implemented yet." );
                } catch ( MalformedURLException e ) {
                    LOG.warn( "Not inlining remote feature reference -- not a valid URI." );
                    writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                    writeAttributeWithNS( XLNNS, "href", ref.getURI() );
                    endEmptyElement();
                }
            } else {
                // must be exported by reference
                writeEmptyElementWithNS( propName.getNamespaceURI(), propName.getLocalPart() );
                writeAttributeWithNS( XLNNS, "href", ref.getURI() );
                endEmptyElement();
            }
        }
    }

    public void export( TypedObjectNode node, int currentLevel, int maxInlineLevels )
                            throws XMLStreamException, UnknownCRSException, TransformationException {
        if ( node instanceof GMLObject ) {
            if ( node instanceof Feature ) {
                export( (Feature) node, currentLevel, maxInlineLevels );
            } else if ( node instanceof Geometry ) {
                gmlStreamWriter.getGeometryWriter().export( (Geometry) node );
            } else {
                throw new UnsupportedOperationException();
            }
        } else if ( node instanceof ElementNode ) {
            ElementNode xmlContent = (ElementNode) node;
            QName elName = xmlContent.getName();
            writeStartElementWithNS( elName.getNamespaceURI(), elName.getLocalPart() );
            if ( xmlContent.getAttributes() != null ) {
                for ( Entry<QName, PrimitiveValue> attr : xmlContent.getAttributes().entrySet() ) {
                    StAXExportingHelper.writeAttribute( writer, attr.getKey(), attr.getValue().getAsText() );
                }
            }
            if ( xmlContent.getChildren() != null ) {
                for ( TypedObjectNode childNode : xmlContent.getChildren() ) {
                    export( childNode, currentLevel, maxInlineLevels );
                }
            }
            writer.writeEndElement();
        } else if ( node instanceof PrimitiveValue ) {
            writer.writeCharacters( ( (PrimitiveValue) node ).getAsText() );
        } else if ( node instanceof TypedObjectNodeArray<?> ) {
            for ( TypedObjectNode elem : ( (TypedObjectNodeArray<?>) node ).getElements() ) {
                export( elem, currentLevel, maxInlineLevels );
            }
        } else if ( node == null ) {
            LOG.warn( "Null node encountered!?" );
        } else {
            throw new RuntimeException( "Unhandled node type '" + node.getClass() + "'" );
        }
    }

    private boolean isPropertyRequested( QName propName ) {
        return projections.isEmpty() || projections.containsKey( propName );
    }
}
